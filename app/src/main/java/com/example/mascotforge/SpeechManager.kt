package com.mascotforge.character

import android.content.Context
import android.util.Log
import com.mascotforge.speech.SpeechContext
import org.json.JSONObject
import com.mascotforge.speech.SpeechContextFactory
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
    fun loadMetadata(basePath: String, isAssets: Boolean = true): CharacterMetadata? {
        return try {
            val jsonStr = if (isAssets) {
                context.assets.open("$basePath/character.json")
                    .bufferedReader()
                    .use { it.readText() }
            } else {
                val file = File(context.filesDir, "$basePath/character.json")
                if (!file.exists()) {
                    Log.w(TAG, "Metadata file not found: ${file.path}")
                    return null
                }
                file.readText()
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
            Log.e(TAG, "Error loading metadata: $basePath", e)
            null
        }
    }

    /**
     * キャラクター読み込み（SpeechRules対応版）
     */
    fun loadCharacter(basePath: String, isAssets: Boolean = true): CharacterProvider? {
        return try {
            val meta = loadMetadata(basePath, isAssets) ?: run {
                Log.e(TAG, "Failed to load metadata for: $basePath")
                return null
            }

            if (meta.speechRules.isNotEmpty()) {
                Log.d(TAG, "Using SpeechRules system for: $basePath")
                return loadCharacterWithRules(basePath, meta, isAssets)
            } else {
                Log.d(TAG, "Using legacy system for: $basePath")
                return loadCharacterLegacy(basePath, meta, isAssets)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character: $basePath", e)
            null
        }
    }

    /**
     * 🔥 SpeechRulesを使った新方式のロード
     *
     * 【重要な変更】
     * - 初回読み込み時は空のセリフマップを渡す
     * - loaderへの参照を渡して、DynamicCharacter内で動的に再評価できるようにする
     */
    private fun loadCharacterWithRules(
        basePath: String,
        meta: CharacterMetadata,
        isAssets: Boolean
    ): CharacterProvider? {
        return try {
            // 🔥 初期状態では空のマップを渡す
            // DynamicCharacter側が必要に応じて動的にロードする
            val emptySpeeches = mapOf<String, List<String>>()

            DynamicCharacter(
                charId = meta.id,
                metadata = meta,
                speeches = emptySpeeches,
                context = context,
                basePath = basePath,
                isAssets = isAssets,
                loader = this  // ← loaderへの参照を渡す
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character with rules: $basePath", e)
            null
        }
    }

    /**
     * 旧方式のロード（互換性維持）
     */
    private fun loadCharacterLegacy(
        basePath: String,
        meta: CharacterMetadata,
        isAssets: Boolean
    ): CharacterProvider? {
        return try {
            val speeches = loadAllSpeeches(basePath, isAssets)

            DynamicCharacter(
                charId = meta.id,
                metadata = meta,
                speeches = speeches,
                context = context,
                basePath = basePath,
                isAssets = isAssets,
                loader = null  // 旧方式ではloaderは不要
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character (legacy): $basePath", e)
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

                val conditions = mutableMapOf<String, String>()
                val conditionsJson = ruleJson.optJSONObject("conditions")
                conditionsJson?.keys()?.forEach { key ->
                    conditions[key] = conditionsJson.getString(key)
                }

                rules.add(SpeechRule(
                    file = ruleJson.getString("file"),
                    conditions = conditions,
                    priority = ruleJson.optInt("priority", 0)
                ))

                Log.d(TAG, "Parsed rule: priority=${ruleJson.optInt("priority", 0)}, file=${ruleJson.getString("file")}, conditions=${conditions.size}")
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
    private fun loadAllSpeeches(basePath: String, isAssets: Boolean): Map<String, List<String>> {
        val speechFiles = listOf(
            "morning.txt",
            "afternoon.txt",
            "evening.txt",
            "night.txt",
            "midnight.txt"
        )

        val speeches = mutableMapOf<String, List<String>>()

        speechFiles.forEach { filename ->
            val key = filename.removeSuffix(".txt")
            val speechPath = "$basePath/speeches/$filename"
            speeches[key] = loadSpeechFile(speechPath, isAssets)
        }

        return speeches
    }

    /**
     * 単一のセリフファイルを読み込む（publicに変更）
     * DynamicCharacterから動的に呼ばれるようになった
     */
    fun loadSpeechFile(path: String, isAssets: Boolean): List<String> {
        return try {
            val lines = if (isAssets) {
                context.assets.open(path).use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                        .readLines()
                }
            } else {
                val file = File(context.filesDir, path)
                if (file.exists()) {
                    file.readLines()
                } else {
                    Log.w(TAG, "Speech file not found: ${file.path}")
                    emptyList()
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
 */
data class SpeechRule(
    val file: String,
    val conditions: Map<String, String>,
    val priority: Int
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