// 実態: SafeCharacterLoader（ZIPキャラクター用ローダー）
// ファイル名は歴史的経緯。クラス名で判断すること。
package com.example.mascotforge.character

import android.content.Context
import android.util.Log
import com.example.mascotforge.speech.SpeechContext
import org.json.JSONObject
import com.example.mascotforge.speech.SpeechContextFactory
import java.io.BufferedReader
import com.example.mascotforge.CharacterProvider
import java.io.File
import java.io.InputStreamReader

/**
 * セキュアなキャラクター読み込みシステム v5（動的再評価対応）
 *
 * 【修正内容】
 * - loadSpeechFile()をpublicに変更してDynamicCharacterから呼べるように
 * - DynamicCharacterにloaderへの参照を渡すように修正
 */
class SafeCharacterLoader(private val context: Context) {

    companion object {
        private const val TAG = "SafeCharacterLoader"
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB
        private const val MAX_LINES = 10000
        private const val CACHE_DURATION = 1 * 60 * 1000L // 1分
    }

    @Volatile
    private var lastContextTime = 0L
    @Volatile
    private var cachedContext: SpeechContext? = null

    /**
     * 現在のSpeechContextを取得
     */
    fun getCurrentContext(): SpeechContext {
        val now = System.currentTimeMillis()

        if (now - lastContextTime < CACHE_DURATION && cachedContext != null) {
            return cachedContext!!
        }

        val speechContext = SpeechContextFactory.create(context)

        cachedContext = speechContext
        lastContextTime = now

        return speechContext
    }

    /**
     * キャッシュクリア
     */
    fun clearCache() {
        cachedContext = null
        lastContextTime = 0L
        Log.d(TAG, "Cache cleared")
    }

    /**
     * メタデータ読み込み（公開メソッド）
     */
    fun loadMetadata(source: CharacterSource): CharacterMetadata? {
        return try {
            val jsonStr = when (source) {
                is CharacterSource.Assets -> context.assets.open("${source.basePath}/character.json")
                    .bufferedReader()
                    .use { it.readText() }
                is CharacterSource.InstalledFiles -> {
                    val file = File(context.filesDir, "${source.basePath}/character.json")
                    if (!file.exists()) {
                        Log.w(TAG, "Metadata file not found: ${file.path}")
                        return null
                    }
                    file.readText()
                }
            }

            val json = JSONObject(jsonStr)

            CharacterMetadata(
                id = json.getString("id"),
                name = json.getString("name"),
                version = json.optString("version", "1.0.0"),
                author = json.optString("author", "Unknown"),
                description = json.optString("description", ""),
                emotionRules = parseEmotionRules(json.optJSONObject("emotions")),
                imageMapping = parseImageMapping(json.optJSONObject("images")),
                customVariables = parseCustomVariables(json.optJSONObject("customVariables")),
                speechRules = parseSpeechRules(json)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata: ${source.basePath}", e)
            null
        }
    }

    /**
     * キャラクター読み込み（SpeechRules対応版）
     */
    fun loadCharacter(source: CharacterSource): CharacterProvider? {
        return try {
            val meta = loadMetadata(source) ?: run {
                Log.e(TAG, "Failed to load metadata for: ${source.basePath}")
                return null
            }

            if (meta.speechRules.isNotEmpty()) {
                Log.d(TAG, "Using SpeechRules system for: ${source.basePath}")
                return loadCharacterWithRules(source, meta)
            } else {
                Log.d(TAG, "Using legacy system for: ${source.basePath}")
                return loadCharacterLegacy(source, meta)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character: ${source.basePath}", e)
            null
        }
    }

    /**
     * SpeechRulesを使った新方式のロード
     */
    private fun loadCharacterWithRules(
        source: CharacterSource,
        meta: CharacterMetadata
    ): CharacterProvider? {
        return try {
            DynamicCharacter(
                charId = meta.id,
                metadata = meta,
                speeches = emptyMap(),
                context = context,
                source = source,
                loader = this
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character with rules: ${source.basePath}", e)
            null
        }
    }

    /**
     * 旧方式のロード（互換性維持）
     */
    private fun loadCharacterLegacy(
        source: CharacterSource,
        meta: CharacterMetadata
    ): CharacterProvider? {
        return try {
            val speeches = loadAllSpeeches(source)

            DynamicCharacter(
                charId = meta.id,
                metadata = meta,
                speeches = speeches,
                context = context,
                source = source,
                loader = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character (legacy): ${source.basePath}", e)
            null
        }
    }

    /**
     * character.jsonからspeechRulesをパース
     */
    private fun parseSpeechRules(json: JSONObject): List<SpeechRule> {
        val rulesArray = json.optJSONArray("speechRules") ?: return emptyList()
        val rules = mutableListOf<SpeechRule>()

        for (i in 0 until rulesArray.length()) {
            try {
                val ruleJson = rulesArray.getJSONObject(i)

                // allOf 条件（AND）: "conditions" または "allOf" キーで指定
                val conditions = mutableMapOf<String, String>()
                (ruleJson.optJSONObject("conditions") ?: ruleJson.optJSONObject("allOf"))
                    ?.keys()?.forEach { key ->
                        conditions[key] = (ruleJson.optJSONObject("conditions") ?: ruleJson.optJSONObject("allOf")!!).getString(key)
                    }

                // anyOf 条件（OR・十分条件）
                val anyOf = mutableMapOf<String, String>()
                ruleJson.optJSONObject("anyOf")?.keys()?.forEach { key ->
                    anyOf[key] = ruleJson.optJSONObject("anyOf")!!.getString(key)
                }

                // ファイル指定: "file" (単体) または "files" (複数・ランダム選択)
                val files: List<String> = when {
                    ruleJson.has("files") -> {
                        val arr = ruleJson.getJSONArray("files")
                        List(arr.length()) { j -> arr.getString(j) }
                    }
                    ruleJson.has("file") -> listOf(ruleJson.getString("file"))
                    else -> {
                        Log.w(TAG, "Speech rule at index $i has no 'file' or 'files', skipping")
                        continue
                    }
                }

                rules.add(SpeechRule(
                    files = files,
                    conditions = conditions,
                    anyOf = anyOf,
                    priority = ruleJson.optInt("priority", 0)
                ))

                Log.d(TAG, "Parsed rule: priority=${ruleJson.optInt("priority", 0)}, files=${files.size}, conditions=${conditions.size}, anyOf=${anyOf.size}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse speech rule at index $i", e)
            }
        }

        val sortedRules = rules.sortedByDescending { it.priority }
        Log.d(TAG, "Loaded ${sortedRules.size} speech rules")
        return sortedRules
    }

    /**
     * 感情判定ルールをパース
     */
    private fun parseEmotionRules(emotionsJson: JSONObject?): List<EmotionRule> {
        if (emotionsJson == null) return emptyList()

        val rules = mutableListOf<EmotionRule>()
        val rulesArray = emotionsJson.optJSONArray("rules") ?: return emptyList()

        for (i in 0 until rulesArray.length()) {
            val ruleJson = rulesArray.getJSONObject(i)

            if (ruleJson.has("default")) {
                rules.add(EmotionRule(
                    condition = null,
                    emotion = ruleJson.getString("default"),
                    isDefault = true
                ))
            } else if (ruleJson.has("if")) {
                rules.add(EmotionRule(
                    condition = ruleJson.getString("if"),
                    emotion = ruleJson.getString("emotion"),
                    isDefault = false
                ))
            }
        }

        return rules
    }

    /**
     * 画像マッピングをパース
     */
    private fun parseImageMapping(imagesJson: JSONObject?): Map<String, String> {
        if (imagesJson == null) return emptyMap()

        val mapping = mutableMapOf<String, String>()
        imagesJson.keys().forEach { key ->
            mapping[key] = imagesJson.getString(key)
        }
        return mapping
    }

    /**
     * カスタム変数のパース
     */
    private fun parseCustomVariables(varsJson: JSONObject?): List<CustomVariable> {
        if (varsJson == null) return emptyList()

        val variables = mutableListOf<CustomVariable>()

        varsJson.keys().forEach { varName ->
            try {
                val varJson = varsJson.getJSONObject(varName)
                val variable = parseCustomVariable(varName, varJson)
                variables.add(variable)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse custom variable: $varName", e)
            }
        }

        return variables
    }

    /**
     * 全てのセリフファイルを読み込む（旧方式）
     */
    private fun loadAllSpeeches(source: CharacterSource): Map<String, List<String>> {
        val speechFiles = listOf(
            "morning.txt",
            "afternoon.txt",
            "evening.txt",
            "night.txt",
            "midnight.txt"
        )

        return speechFiles.associate { filename ->
            val key = filename.removeSuffix(".txt")
            val speechPath = "${source.basePath}/speeches/$filename"
            key to loadSpeechFile(speechPath, source)
        }
    }

    /**
     * 単一のセリフファイルを読み込む
     * DynamicCharacterから動的に呼ばれる
     */
    fun loadSpeechFile(path: String, source: CharacterSource): List<String> {
        return try {
            // パストラバーサル防止
            if (path.contains("..") || !path.startsWith(source.basePath)) {
                Log.e(TAG, "Invalid speech file path: $path")
                return emptyList()
            }

            val lines = when (source) {
                is CharacterSource.Assets -> context.assets.open(path).use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readLines()
                }
                is CharacterSource.InstalledFiles -> {
                    val file = File(context.filesDir, path)
                    if (file.exists()) {
                        file.readLines()
                    } else {
                        Log.w(TAG, "Speech file not found: ${file.path}")
                        emptyList()
                    }
                }
            }

            val filteredLines = lines
                .take(MAX_LINES)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }

            Log.d(TAG, "Loaded ${filteredLines.size} speeches from: $path")
            filteredLines
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load speech file: $path", e)
            emptyList()
        }
    }
}

/**
 * セリフルール（優先度付き条件分岐）
 *
 * 【条件の仕組み】
 * - conditions (allOf): 全エントリが AND 結合。全部一致で適合。省略可。
 * - anyOf:             任意の1エントリが OR 結合。1つでも一致で適合。省略可。
 * - 両方指定時は conditions AND anyOf の両方を満たす必要がある。
 *
 * 【ファイルの仕組み】
 * - files: 候補リスト。適合時にランダムで1つ選ばれる。
 * - JSON では "file": "foo.txt"（単体）か "files": ["a.txt","b.txt"]（複数）で指定。
 */
data class SpeechRule(
    val files: List<String>,                       // 候補ファイルリスト（ランダム選択）
    val conditions: Map<String, String> = emptyMap(), // AND 条件
    val anyOf: Map<String, String> = emptyMap(),      // OR 条件（十分条件）
    val priority: Int = 0
)

/**
 * キャラクターメタ情報
 */
data class CharacterMetadata(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val emotionRules: List<EmotionRule>,
    val imageMapping: Map<String, String>,
    val customVariables: List<CustomVariable> = emptyList(),
    val speechRules: List<SpeechRule> = emptyList()
)

/**
 * 感情判定ルール
 */
data class EmotionRule(
    val condition: String?,
    val emotion: String,
    val isDefault: Boolean = false
)