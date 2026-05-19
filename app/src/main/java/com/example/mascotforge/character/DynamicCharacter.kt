package com.example.mascotforge.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.example.mascotforge.speech.SpeechContext
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.character.speech.TagParser
import com.example.mascotforge.character.speech.OperationType
import java.io.File

/**
 * 動的生成されたキャラクター（CustomVariableManager統合版）
 *
 * 【修正内容】
 * - CustomVariableManagerが統合版に対応
 * - variableDefinitionsをコンストラクタで渡す
 * - triggerRules()の引数が変更
 * - TagParserとCustomVariableManagerが同じデータを共有
 */
class DynamicCharacter(
    private val charId: String,
    private val metadata: CharacterMetadata,
    private var speeches: Map<String, List<String>>, // 初期値用（旧方式互換）
    private val context: Context,
    private val source: CharacterSource,
    private val loader: SafeCharacterLoader? = null
) : CharacterProvider {

    companion object {
        private const val TAG = "DynamicCharacter"
    }

    override val id: String = metadata.id
    override val name: String = metadata.name

    // 統合版CustomVariableManager（variableDefinitionsを渡す）
    private val variableManager = CustomVariableManager(
        context = context,
        characterId = charId,
        variableDefinitions = metadata.customVariables
    )

    @Volatile
    private var lastTimeSlot: String? = null
    private var extractedEmotionKey: String? = null

    // アクティブShellのキャッシュ（ディスクI/Oを毎フレーム行わないよう保持）
    // null = 「未ロード」、CachedShell.shell = null = 「Shellなし確定」
    @Volatile
    private var cachedShell: CachedShell? = null

    private data class CachedShell(val shellId: String?, val shell: Shell?)

    // 現在ロード中のファイル名を記録
    private var currentSpeechFile: String? = null

    // キャッシュされたセリフリスト
    private var cachedSpeeches: List<String> = emptyList()

    // 表示済みセリフ（重複抑制用）
    private val shownSpeeches = mutableSetOf<String>()

    /**
     * セリフ生成（統合版対応）
     */
    override suspend fun getSpeech(ctx: SpeechContext): String? {
        // 1. 起動時トリガーを実行
        if (ctx.isFirstLaunchToday) {
            Log.d(TAG, "[$charId] First launch today - triggering ON_LAUNCH")
            variableManager.triggerRules(
                trigger = CustomVariable.ChangeRule.Trigger.ON_LAUNCH,
                speechContext = ctx
            )
        }

        // 2. 連続起動日数変化トリガー
        variableManager.triggerRules(
            trigger = CustomVariable.ChangeRule.Trigger.ON_CONSECUTIVE_DAYS,
            speechContext = ctx
        )

        // 3. 時間帯変化トリガー
        if (lastTimeSlot != null && lastTimeSlot != ctx.timeSlot) {
            Log.d(TAG, "[$charId] TimeSlot changed: $lastTimeSlot -> ${ctx.timeSlot}")
            variableManager.triggerRules(
                trigger = CustomVariable.ChangeRule.Trigger.ON_TIME_SLOT_CHANGE,
                speechContext = ctx
            )
        }
        lastTimeSlot = ctx.timeSlot

        // 4. セリフリストを取得（動的再評価）
        val timeSlotSpeeches = getSpeeches(ctx)

        if (timeSlotSpeeches.isEmpty()) {
            Log.w(TAG, "[$charId] No speeches available")
            extractedEmotionKey = null
            return null
        }

        // 5. 重複を避けてセリフを選択
        val unshown = timeSlotSpeeches.filter { it !in shownSpeeches }
        val template: String
        if (unshown.isNotEmpty()) {
            template = unshown.random()
            if (unshown.size <= 1) currentSpeechFile = null  // 次回別ファイルを試す
        } else {
            currentSpeechFile = null
            shownSpeeches.clear()
            val freshSpeeches = getSpeeches(ctx)
            if (freshSpeeches.isEmpty()) {
                Log.w(TAG, "[$charId] No speeches available after reset")
                extractedEmotionKey = null
                return null
            }
            template = freshSpeeches.random()
        }
        shownSpeeches += template

        // 6. TagParserを作成（統合版のCharacterStateを使用）
        val parser = createTagParser()
        val parsedSpeech = parser.parseAndApply(template)  // 自動保存される

        extractedEmotionKey = parsedSpeech.emotion

        if (parsedSpeech.emotion != null) {
            Log.d(TAG, "[$charId] Emotion tag extracted: ${parsedSpeech.emotion}")
        }

        // [var:] 操作を CustomVariableManager に適用（型変換・範囲制限を含む）
        parsedSpeech.variableOperations.forEach { op ->
            when (op.type) {
                OperationType.SET_STRING -> {
                    val strVal = op.stringValue ?: return@forEach
                    val varDef = metadata.customVariables.find { it.name == op.varName } ?: run {
                        Log.w(TAG, "[$charId] [var:] '${op.varName}' が未定義のためスキップ")
                        return@forEach
                    }
                    when (varDef.type) {
                        CustomVariable.VariableType.STRING -> {
                            if (varDef.options != null && strVal !in varDef.options) {
                                Log.w(TAG, "[$charId] [var:] '$strVal' は '${op.varName}' のoptions外のためスキップ")
                                return@forEach
                            }
                            variableManager.setValue(op.varName, strVal)
                            Log.d(TAG, "[$charId] [var:] ${op.varName} = \"$strVal\"")
                        }
                        CustomVariable.VariableType.BOOLEAN -> {
                            variableManager.setValue(op.varName, strVal.toBoolean())
                            Log.d(TAG, "[$charId] [var:] ${op.varName} = ${strVal.toBoolean()}")
                        }
                        CustomVariable.VariableType.NUMBER -> {
                            Log.w(TAG, "[$charId] [var:] NUMBER型 '${op.varName}' にSET_STRINGは不可、スキップ")
                        }
                    }
                }
                OperationType.TOGGLE -> {
                    val varDef = metadata.customVariables.find { it.name == op.varName } ?: run {
                        Log.w(TAG, "[$charId] [var:] TOGGLE対象 '${op.varName}' が未定義のためスキップ")
                        return@forEach
                    }
                    if (varDef.type != CustomVariable.VariableType.BOOLEAN) {
                        Log.w(TAG, "[$charId] [var:] TOGGLE はBOOLEAN型のみ有効 ('${op.varName}' は ${varDef.type})")
                        return@forEach
                    }
                    val current = variableManager.getValue(op.varName) as? Boolean ?: false
                    variableManager.setValue(op.varName, !current)
                    Log.d(TAG, "[$charId] [var:] ${op.varName}: $current -> ${!current} (TOGGLE)")
                }
                else -> {
                    // 数値操作 (ADD / SUBTRACT / MULTIPLY / DIVIDE / SET)
                    val current = variableManager.getValue(op.varName) as? Int ?: run {
                        Log.w(TAG, "[$charId] [var:] '${op.varName}' は NUMBER 型でないか未定義のためスキップ")
                        return@forEach
                    }
                    val newValue = when (op.type) {
                        OperationType.ADD      -> current + op.operationValue
                        OperationType.SUBTRACT -> current - op.operationValue
                        OperationType.MULTIPLY -> current * op.operationValue
                        OperationType.DIVIDE   -> if (op.operationValue != 0) current / op.operationValue else current
                        OperationType.SET      -> op.operationValue
                        else                   -> current
                    }
                    variableManager.setValue(op.varName, newValue)
                    Log.d(TAG, "[$charId] [var:] ${op.varName}: $current -> $newValue (${op.type} ${op.operationValue})")
                }
            }
        }

        if (parsedSpeech.variableOperations.isNotEmpty()) {
            Log.d(TAG, "[$charId] Variable operations applied: ${parsedSpeech.variableOperations.size}")
        }

        // 7. セリフ表示トリガーを実行
        variableManager.triggerRules(
            trigger = CustomVariable.ChangeRule.Trigger.ON_SPEECH,
            speechContext = ctx
        )

        // 8. 変数展開（標準変数 + カスタム変数）
        val expandedSpeech = expandVariables(parsedSpeech.cleanedText, ctx)

        Log.d(TAG, "[$charId] Speech: $expandedSpeech")
        return expandedSpeech
    }

    /**
     * 🔥 TagParserを作成（統合版のCharacterStateを使用）
     */
    private fun createTagParser(): TagParser {
        val stateManager = CharacterStateManager(context)
        return TagParser(
            characterState = variableManager.getCharacterState(),
            stateManager = stateManager,
            characterId = charId
        )
    }

    /**
     * セリフリストを取得（動的再評価対応版）
     */
    private fun getSpeeches(ctx: SpeechContext): List<String> {
        // SpeechRules方式（動的再評価）
        if (metadata.speechRules.isNotEmpty() && loader != null) {
            val selectedFile = selectSpeechFile(ctx, metadata.speechRules)

            if (selectedFile == null) {
                Log.e(TAG, "[$charId] No matching speech file found")
                return emptyList()
            }

            // 前回と違うファイルが選択された場合のみ再ロード
            if (selectedFile != currentSpeechFile) {
                Log.d(TAG, "[$charId] Speech file changed: $currentSpeechFile -> $selectedFile")

                val speechPath = "${source.basePath}/$selectedFile"
                cachedSpeeches = loader.loadSpeechFile(speechPath, source)
                currentSpeechFile = selectedFile
                shownSpeeches.clear()

                Log.d(TAG, "[$charId] Loaded ${cachedSpeeches.size} speeches from: $selectedFile")
            }

            return cachedSpeeches
        }

        // 旧方式（時間帯ごとの固定ファイル）
        if (speeches.containsKey("current")) {
            return speeches["current"] ?: emptyList()
        }

        return speeches[ctx.timeSlot] ?: speeches["morning"] ?: emptyList()
    }

    /**
     * 現在のコンテキストに合致するセリフファイルを選択
     *
     * 【マッチング仕様】
     * - conditions (AND): 全条件が一致 かつ
     * - anyOf (OR): anyOf が空 または anyOf の少なくとも1条件が一致
     * - 両方空 = デフォルトルール
     *
     * 【ファイル選択】
     * - 複数ファイルが設定されている場合はランダムに1つ選択
     */
    private fun selectSpeechFile(context: SpeechContext, rules: List<SpeechRule>): String? {
        val defaultRule = rules.find { it.conditions.isEmpty() && it.anyOf.isEmpty() }
        val conditionalRules = rules.filter { it.conditions.isNotEmpty() || it.anyOf.isNotEmpty() }

        // 1. 条件付きルールを優先度の高い順にチェック
        for (rule in conditionalRules.sortedByDescending { it.priority }) {
            val allOfMatches = rule.conditions.isEmpty() || context.matchesAll(rule.conditions)
            val anyOfMatches = rule.anyOf.isEmpty() || context.matchesAny(rule.anyOf)

            if (allOfMatches && anyOfMatches) {
                val selectedFile = rule.files.random()
                Log.d(TAG, "[$charId] Matched rule (priority ${rule.priority}): $selectedFile" +
                        if (rule.files.size > 1) " (${rule.files.size} candidates)" else "")
                return selectedFile
            }
        }

        // 2. デフォルトルールを使う
        if (defaultRule != null) {
            val selectedFile = defaultRule.files.random()
            Log.d(TAG, "[$charId] Using default rule: $selectedFile")
            return selectedFile
        }

        Log.w(TAG, "[$charId] No matching rule found")
        return null
    }

    /**
     * 感情判定 → 画像取得
     *
     * 画像解決の優先順位:
     *   1. アクティブShellのemotionMapping[emotion] → shells/{id}/images/
     *   2. キャラクター本来のimageMapping[emotion]  → source.basePath/images/
     */
    override fun getCharaImage(ctx: SpeechContext): Bitmap? {
        var emotion = "normal"

        // 1. アクティブShellを先に取得（感情タグ検証でも使うため）
        val shell = resolveActiveShell()

        if (extractedEmotionKey != null) {
            val knownInCharacter = metadata.imageMapping.containsKey(extractedEmotionKey)
            val knownInShell     = shell?.emotionMapping?.containsKey(extractedEmotionKey) == true

            if (knownInCharacter || knownInShell) {
                emotion = extractedEmotionKey!!
                Log.d(TAG, "[$charId] Using emotion from tag: $emotion" +
                        if (knownInShell && !knownInCharacter) " (shell-only)" else "")
            } else {
                Log.w(TAG, "[$charId] Unknown emotion tag: '$extractedEmotionKey'. " +
                        "Character: ${metadata.imageMapping.keys.joinToString()}, " +
                        "Shell: ${shell?.emotionMapping?.keys?.joinToString() ?: "none"}. " +
                        "Falling back to emotionRules.")
                emotion = determineEmotion(ctx)
            }
        } else {
            emotion = determineEmotion(ctx)
            Log.d(TAG, "[$charId] Using emotion from rules: $emotion")
        }

        extractedEmotionKey = null

        if (shell != null) {
            val shellFileName = shell.emotionMapping[emotion]
            if (shellFileName != null) {
                val shellBitmap = loadShellImage(shell.id, shellFileName)
                if (shellBitmap != null) {
                    Log.d(TAG, "[$charId] Using shell image: ${shell.id}/$shellFileName")
                    return shellBitmap
                }
                Log.w(TAG, "[$charId] Shell image not found, falling back to character image.")
            } else {
                Log.d(TAG, "[$charId] Shell has no mapping for '$emotion', falling back.")
            }
        }

        // 2. キャラクター本来の画像
        val imageFileName = metadata.imageMapping[emotion]
            ?: metadata.imageMapping["normal"]
            ?: metadata.imageMapping.values.firstOrNull()
            ?: "character.png"

        val imagePath = "${source.basePath}/images/$imageFileName"
        return loadImage(imagePath = imagePath, tag = emotion)
    }

    /**
     * アクティブShellをキャッシュ付きで返す。
     *
     * SharedPreferencesのshellIdと比較して変化がなければキャッシュをそのまま返す。
     * shellIdが変わったとき（Shell切替・解除）だけ再読み込みする。
     */
    private fun resolveActiveShell(): Shell? {
        val prefs = context.getSharedPreferences("prefs_shells", Context.MODE_PRIVATE)
        val currentShellId = prefs.getString("active_shell_$charId", null)

        val cached = cachedShell
        if (cached != null && cached.shellId == currentShellId) {
            return cached.shell
        }

        // キャッシュミス: 実際にロードして保存
        val loaded = if (currentShellId != null) {
            ShellRegistry.loadShell(context, currentShellId)
        } else {
            null
        }
        cachedShell = CachedShell(shellId = currentShellId, shell = loaded)
        return loaded
    }

    /**
     * Shell画像の読み込み（filesDir/shells/{shellId}/images/{fileName}）
     */
    private fun loadShellImage(shellId: String, fileName: String): Bitmap? {
        if (fileName.contains("..") || fileName.contains('/') || fileName.contains('\\')) {
            Log.e(TAG, "[$charId] Invalid shell image filename: $fileName")
            return null
        }

        val relativePath = "shells/$shellId/images/$fileName"
        val cacheKey = "shell:$shellId:$fileName"

        CharacterBitmapCache.get(cacheKey)?.let { return it }

        return try {
            val file = File(context.filesDir, relativePath)
            if (!file.isFile) {
                Log.w(TAG, "[$charId] Shell image not found: ${file.path}")
                return null
            }
            BitmapFactory.decodeFile(file.absolutePath)
                ?.also { CharacterBitmapCache.put(cacheKey, it) }
        } catch (e: Exception) {
            Log.w(TAG, "[$charId] Failed to load shell image: $relativePath", e)
            null
        }
    }

    /**
     * 感情判定（ルールベース）
     */
    private fun determineEmotion(ctx: SpeechContext): String {
        var defaultEmotion: String? = null

        for (rule in metadata.emotionRules) {
            if (rule.isDefault) {
                // デフォルトはフォールバック用に記録だけして先へ
                defaultEmotion = rule.emotion
                continue
            }

            val condition = rule.condition ?: continue

            if (evaluateCondition(condition, ctx)) {
                return rule.emotion
            }
        }

        // 条件付きルールが全部外れたときだけ default を使う
        return defaultEmotion ?: "normal"
    }

    /**
     * 条件式を安全に評価
     */
    private fun evaluateCondition(condition: String, ctx: SpeechContext): Boolean {
        return try {
            val customValues = variableManager.getAllValues(metadata.customVariables)
            val evaluator = SafeExpressionEvaluator(ctx, customValues)
            evaluator.evaluate(condition)
        } catch (e: Exception) {
            Log.e(TAG, "[$charId] Error evaluating condition: $condition", e)
            false
        }
    }

    /**
     * 変数展開（標準変数 + カスタム変数）
     */
    private fun expandVariables(template: String, ctx: SpeechContext): String {
        var result = template

        // 1. 標準変数の展開
        val standardVariables = mapOf(
            "userName" to ctx.userName,
            "hour" to ctx.hour.toString(),
            "minute" to ctx.minute.toString(),
            "month" to ctx.month.toString(),
            "day" to ctx.day.toString(),
            "dayOfWeek" to ctx.dayOfWeek,
            "timeSlot" to ctx.timeSlot,
            "weatherCode" to ctx.weatherCode,
            "weatherEmoji" to ctx.weatherEmoji,
            "temperature" to ctx.temperature.toString(),
            "temperatureFeeling" to ctx.temperatureFeeling,
            "batteryLevel" to ctx.batteryLevel.toString(),
            "batteryStatus" to ctx.batteryStatus,
            "isCharging" to ctx.isCharging.toString(),
            "isLowBattery" to ctx.isLowBattery.toString(),
            "season" to ctx.season,
            "specialDayName" to (ctx.specialDayName ?: ""),
            "holidayName" to (ctx.holidayName ?: ""),
            "isWeekend" to ctx.isWeekend.toString(),
            "isHoliday" to ctx.isHoliday.toString(),
            "launchCount" to ctx.launchCount.toString(),
            "consecutiveDays" to ctx.consecutiveDays.toString(),
            "lastLaunchHoursAgo" to (ctx.lastLaunchHoursAgo?.toString() ?: "0"),
            "isFirstLaunchToday" to ctx.isFirstLaunchToday.toString(),
            "wasTouched" to ctx.wasTouched.toString(),
            "touchCount" to ctx.touchCount.toString(),
            "touchCountToday" to ctx.touchCountToday.toString(),
            "lastTouchMinutesAgo" to ctx.lastTouchMinutesAgo.toString(),
            "consecutiveTouchCount" to ctx.consecutiveTouchCount.toString(),
            "pettingLevel" to ctx.pettingLevel.toString(),
            "isBeingPetted" to ctx.isBeingPetted.toString()
        )

        standardVariables.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        // 2. カスタム変数の展開
        val customValues = variableManager.getAllValues(metadata.customVariables)
        customValues.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        return result
    }

    /**
     * 画像読み込み（Assets or InstalledFiles）
     * CharacterBitmapCache でメモリキャッシュする。
     * 赤背景を透明化する処理も含む
     */
    private fun loadImage(imagePath: String, tag: String?): Bitmap? {
        if (imagePath.contains("..") || !imagePath.startsWith(source.basePath)) {
            Log.e(TAG, "[$charId] Invalid image path: $imagePath")
            return null
        }

        val settings = CharacterSettingsLoader.load(context, source)
        val cutoutSpec = settings?.findCutoutSpec(tag)
        val tolerance = when {
            cutoutSpec?.tolerance != null -> cutoutSpec.tolerance
            settings?.imageCutout != null -> settings.imageCutout.defaultTolerance
            else -> null
        }
        val colors = cutoutSpec?.colors ?: emptyList()

        val cacheKey = if (colors.isEmpty() || tolerance == null) {
            "$charId:$imagePath:raw"
        } else {
            "$charId:$imagePath:cutout:$tag:t$tolerance:${colors.joinToString(separator = ",") { it.toUInt().toString(16) }}"
        }
        CharacterBitmapCache.get(cacheKey)?.let { return it }

        return try {
            val bitmap = when (source) {
                is CharacterSource.Assets -> context.assets.open(imagePath).use { input ->
                    BitmapFactory.decodeStream(input)
                }
                is CharacterSource.InstalledFiles -> {
                    val file = File(context.filesDir, imagePath)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        Log.w(TAG, "[$charId] Image file not found: ${file.path}")
                        null
                    }
                }
            }
            bitmap?.let { raw ->
                val processed = if (colors.isEmpty() || tolerance == null) {
                    raw
                } else {
                    applyColorCutout(raw, colors, tolerance)
                }
                CharacterBitmapCache.put(cacheKey, processed)
                processed
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$charId] Failed to load image: $imagePath", e)
            null
        }
    }

    /**
     * 指定色（最大10色想定）を透明に置き換える
     */
    private fun applyColorCutout(src: Bitmap, targetColors: List<Int>, tolerance: Int): Bitmap {
        val targets = targetColors.map {
            Triple((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF)
        }
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        val toleranceSq = tolerance * tolerance

        for (i in pixels.indices) {
            val p = pixels[i]
            // すでに透明なら判定不要
            if ((p ushr 24) == 0) continue

            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            var hit = false
            for (t in targets) {
                val dr = r - t.first
                val dg = g - t.second
                val db = b - t.third
                val distSq = dr * dr + dg * dg + db * db
                if (distSq < toleranceSq) {
                    hit = true
                    break
                }
            }
            if (hit) pixels[i] = Color.TRANSPARENT
        }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        src.recycle()
        return result
    }

    override fun isAvailable(): Boolean = true

    // ========================================
    // タッチイベント
    // ========================================

    /**
     * ウィジェットタッチ時に呼ぶ。
     * ON_TOUCH トリガーのカスタム変数ルールを発火する。
     * TimeWidgetProvider から updateSingleWidget() の前に呼ばれる想定。
     */
    fun triggerTouchRules(ctx: SpeechContext) {
        Log.d(TAG, "[$charId] triggerTouchRules: firing ON_TOUCH rules")
        variableManager.triggerRules(
            trigger = CustomVariable.ChangeRule.Trigger.ON_TOUCH,
            speechContext = ctx
        )
    }

    // ========================================
    // デバッグ・管理用メソッド
    // ========================================

    fun resetCustomVariables() {
        variableManager.resetAll()
        Log.d(TAG, "[$charId] Custom variables reset")
    }

    fun logCustomVariables() {
        variableManager.logAllValues()
    }

    fun getCustomVariableValue(varName: String): Any? {
        return variableManager.getValue(varName)
    }

    fun setCustomVariableValue(varName: String, value: Any) {
        if (variableManager.setValue(varName, value)) {
            Log.d(TAG, "[$charId] Manually set $varName = $value")
        } else {
            Log.w(TAG, "[$charId] Variable not found: $varName")
        }
    }
}
