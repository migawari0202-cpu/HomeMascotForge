package com.mascotforge.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.mascotforge.speech.SpeechContext
import com.example.mascotforge.CharacterProvider
import com.mascotforge.character.speech.TagParser
import com.mascotforge.character.speech.OperationType
import java.io.File

/**
 * 動的生成されたキャラクター（SpeechRules動的再評価対応版）
 *
 * 【修正内容】
 * - SafeCharacterLoaderへの参照を保持
 * - getSpeeches()内で毎回SpeechRulesを再評価
 * - 時間帯や条件が変わったら自動的に別のファイルを読み込む
 * - TagParserクラスに責務を移譲
 */
class DynamicCharacter(
    private val charId: String,
    private val metadata: CharacterMetadata,
    private var speeches: Map<String, List<String>>, // 初期値用（旧方式互換）
    private val context: Context,
    private val basePath: String,
    private val isAssets: Boolean = true,
    private val loader: SafeCharacterLoader? = null
) : CharacterProvider {

    companion object {
        private const val TAG = "DynamicCharacter"
    }

    override val id: String = metadata.id
    override val name: String = metadata.name

    private val variableManager = CustomVariableManager(context, charId)
    private val tagParser = TagParser()
    private var lastTimeSlot: String? = null
    private var extractedEmotionKey: String? = null

    // 現在ロード中のファイル名を記録
    private var currentSpeechFile: String? = null

    // キャッシュされたセリフリスト
    private var cachedSpeeches: List<String> = emptyList()

    /**
     * セリフ生成（SpeechRules動的再評価対応版 + TagParser対応）
     */
    override suspend fun getSpeech(ctx: SpeechContext): String? {
        // 1. 起動時トリガーを実行
        if (ctx.isFirstLaunchToday) {
            Log.d(TAG, "[$charId] First launch today - triggering ON_LAUNCH")
            variableManager.triggerRules(
                metadata.customVariables,
                CustomVariable.ChangeRule.Trigger.ON_LAUNCH,
                ctx
            )
        }

        // 2. 連続起動日数変化トリガー
        variableManager.triggerRules(
            metadata.customVariables,
            CustomVariable.ChangeRule.Trigger.ON_CONSECUTIVE_DAYS,
            ctx
        )

        // 3. 時間帯変化トリガー
        if (lastTimeSlot != null && lastTimeSlot != ctx.timeSlot) {
            Log.d(TAG, "[$charId] TimeSlot changed: $lastTimeSlot -> ${ctx.timeSlot}")
            variableManager.triggerRules(
                metadata.customVariables,
                CustomVariable.ChangeRule.Trigger.ON_TIME_SLOT_CHANGE,
                ctx
            )
        }
        lastTimeSlot = ctx.timeSlot

        // 4. 🔥 セリフリストを取得（動的再評価）
        val timeSlotSpeeches = getSpeeches(ctx)

        if (timeSlotSpeeches.isEmpty()) {
            Log.w(TAG, "[$charId] No speeches available")
            extractedEmotionKey = null
            return null
        }

        // 5. ランダムにセリフを選択
        val template = timeSlotSpeeches.random()

        // 6. TagParserでタグを解析
        val parsedSpeech = tagParser.parse(template)
        extractedEmotionKey = parsedSpeech.emotion

        if (parsedSpeech.emotion != null) {
            Log.d(TAG, "[$charId] Emotion tag extracted: ${parsedSpeech.emotion}")
        }

        if (parsedSpeech.variableOperations.isNotEmpty()) {
            Log.d(TAG, "[$charId] Variable operations: ${parsedSpeech.variableOperations.size}")
        }

        // 7. セリフ表示トリガーを実行
        variableManager.triggerRules(
            metadata.customVariables,
            CustomVariable.ChangeRule.Trigger.ON_SPEECH,
            ctx
        )

        // 8. [var: ...]タグで指定された変数操作を実行
        parsedSpeech.variableOperations.forEach { operation ->
            executeVariableOperation(operation)
        }

        // 9. 変数展開（標準変数 + カスタム変数）
        val expandedSpeech = expandVariables(parsedSpeech.cleanedText, ctx)

        Log.d(TAG, "[$charId] Speech: $expandedSpeech")
        return expandedSpeech
    }

    /**
     * 変数操作を実際に実行
     *
     * 存在しない変数名が指定された場合は警告ログを出して何もしない
     */
    private fun executeVariableOperation(operation: com.mascotforge.character.speech.VariableOperation) {
        // character.jsonで定義されているか確認
        val variable = metadata.customVariables.find { it.name == operation.varName }

        if (variable == null) {
            Log.w(TAG, "[$charId] Variable operation ignored: '${operation.varName}' is not defined in character.json")
            Log.w(TAG, "[$charId] Available variables: ${metadata.customVariables.map { it.name }.joinToString()}")
            return // 何もせずに終了
        }

        // 変数が存在する場合のみ操作を実行
        when (operation.type) {
            OperationType.ADD -> {
                val currentValue = variableManager.getValue(variable) as? Int ?: 0
                val newValue = currentValue + (operation.value as Int)
                variableManager.setValue(variable, newValue)
                Log.d(TAG, "[$charId] ${operation.varName}: $currentValue → $newValue (+${operation.value})")
            }

            OperationType.SUBTRACT -> {
                val currentValue = variableManager.getValue(variable) as? Int ?: 0
                val newValue = currentValue - (operation.value as Int)
                variableManager.setValue(variable, newValue)
                Log.d(TAG, "[$charId] ${operation.varName}: $currentValue → $newValue (-${operation.value})")
            }

            OperationType.SET -> {
                variableManager.setValue(variable, operation.value)
                Log.d(TAG, "[$charId] ${operation.varName} = ${operation.value}")
            }
        }
    }

    /**
     * 🔥 セリフリストを取得（動的再評価対応版）
     *
     * SpeechRulesがある場合、毎回条件を再評価してファイルを選び直す
     */
    private fun getSpeeches(ctx: SpeechContext): List<String> {
        // ========================================
        // A. SpeechRules方式（動的再評価）
        // ========================================
        if (metadata.speechRules.isNotEmpty() && loader != null) {
            // 現在のコンテキストで条件を再評価
            val selectedFile = selectSpeechFile(ctx, metadata.speechRules)

            if (selectedFile == null) {
                Log.e(TAG, "[$charId] No matching speech file found")
                return emptyList()
            }

            // 🔥 前回と違うファイルが選択された場合のみ再ロード
            if (selectedFile != currentSpeechFile) {
                Log.d(TAG, "[$charId] Speech file changed: $currentSpeechFile -> $selectedFile")

                val speechPath = "$basePath/$selectedFile"
                cachedSpeeches = loader.loadSpeechFile(speechPath, isAssets)
                currentSpeechFile = selectedFile

                Log.d(TAG, "[$charId] Loaded ${cachedSpeeches.size} speeches from: $selectedFile")
            }

            return cachedSpeeches
        }

        // ========================================
        // B. 旧方式（時間帯ごとの固定ファイル）
        // ========================================
        if (speeches.containsKey("current")) {
            return speeches["current"] ?: emptyList()
        }

        return speeches[ctx.timeSlot] ?: speeches["morning"] ?: emptyList()
    }

    /**
     * 現在のコンテキストに合致するセリフファイルを選択
     *
     * SafeCharacterLoaderのロジックと同じだが、
     * DynamicCharacter内で再評価できるようにコピー
     */
    private fun selectSpeechFile(context: SpeechContext, rules: List<SpeechRule>): String? {
        // デフォルトルールと条件付きルールを分離
        val defaultRule = rules.find { it.conditions.isEmpty() }
        val conditionalRules = rules.filter { it.conditions.isNotEmpty() }

        // 1. 条件付きルールを優先度の高い順にチェック
        for (rule in conditionalRules.sortedByDescending { it.priority }) {
            if (context.matchesAll(rule.conditions)) {
                Log.d(TAG, "[$charId] Matched rule (priority ${rule.priority}): ${rule.file}")
                return rule.file
            }
        }

        // 2. デフォルトルールを使う
        if (defaultRule != null) {
            Log.d(TAG, "[$charId] Using default rule: ${defaultRule.file}")
            return defaultRule.file
        }

        Log.w(TAG, "[$charId] No matching rule found")
        return null
    }

    /**
     * 感情判定 → 画像取得
     */
    override fun getCharaImage(ctx: SpeechContext): Bitmap? {
        var emotion = "normal"

        if (extractedEmotionKey != null) {
            if (metadata.imageMapping.containsKey(extractedEmotionKey)) {
                emotion = extractedEmotionKey!!
                Log.d(TAG, "[$charId] Using emotion from tag: $emotion")
            } else {
                Log.w(TAG, "[$charId] Unknown emotion tag: '$extractedEmotionKey'. " +
                        "Available emotions: ${metadata.imageMapping.keys.joinToString()}. " +
                        "Falling back to emotionRules.")
                emotion = determineEmotion(ctx)
            }
        } else {
            emotion = determineEmotion(ctx)
            Log.d(TAG, "[$charId] Using emotion from rules: $emotion")
        }

        extractedEmotionKey = null

        val imageFileName = metadata.imageMapping[emotion]
            ?: metadata.imageMapping["normal"]
            ?: metadata.imageMapping.values.firstOrNull()
            ?: "character.png"

        val imagePath = "$basePath/images/$imageFileName"
        return loadImage(imagePath)
    }

    /**
     * 感情判定（ルールベース）
     */
    private fun determineEmotion(ctx: SpeechContext): String {
        for (rule in metadata.emotionRules) {
            if (rule.isDefault) {
                return rule.emotion
            }

            val condition = rule.condition ?: continue

            if (evaluateCondition(condition, ctx)) {
                return rule.emotion
            }
        }

        return "normal"
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
            "isFirstLaunchToday" to ctx.isFirstLaunchToday.toString()
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
     * 画像読み込み（Assets or Files）
     */
    private fun loadImage(imagePath: String): Bitmap? {
        return try {
            if (imagePath.contains("..") || !imagePath.startsWith(basePath)) {
                Log.e(TAG, "[$charId] Invalid image path: $imagePath")
                return null
            }

            if (isAssets) {
                context.assets.open(imagePath).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } else {
                val file = File(context.filesDir, imagePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    Log.w(TAG, "[$charId] Image file not found: ${file.path}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$charId] Failed to load image: $imagePath", e)
            null
        }
    }

    override fun isAvailable(): Boolean = true

    // ========================================
    // デバッグ・管理用メソッド
    // ========================================

    fun resetCustomVariables() {
        variableManager.resetAll(metadata.customVariables)
        Log.d(TAG, "[$charId] Custom variables reset")
    }

    fun logCustomVariables() {
        variableManager.logAllValues(metadata.customVariables)
    }

    fun getCustomVariableValue(varName: String): Any? {
        val variable = metadata.customVariables.find { it.name == varName }
        return variable?.let { variableManager.getValue(it) }
    }

    fun setCustomVariableValue(varName: String, value: Any) {
        val variable = metadata.customVariables.find { it.name == varName }
        if (variable != null) {
            variableManager.setValue(variable, value)
            Log.d(TAG, "[$charId] Manually set $varName = $value")
        } else {
            Log.w(TAG, "[$charId] Variable not found: $varName")
        }
    }
}

/**
 * 安全な式評価器（カスタム変数対応版）
 */
class SafeExpressionEvaluator(
    private val ctx: SpeechContext,
    private val customVariables: Map<String, String> = emptyMap()
) {

    fun evaluate(expression: String): Boolean {
        val expr = expression.replace(" ", "")

        if ("||" in expr) {
            return expr.split("||").any { evaluate(it) }
        }

        if ("&&" in expr) {
            return expr.split("&&").all { evaluate(it) }
        }

        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evaluate(expr.substring(1, expr.length - 1))
        }

        return evaluateSingleCondition(expr)
    }

    private fun evaluateSingleCondition(expr: String): Boolean {
        val operators = listOf("==", "!=", "<=", ">=", "<", ">")

        for (op in operators) {
            if (op in expr) {
                val parts = expr.split(op, limit = 2)
                if (parts.size != 2) continue

                val left = parts[0].trim()
                val right = parts[1].trim()

                return compareValues(left, right, op)
            }
        }

        if (expr.startsWith("!")) {
            val field = expr.substring(1)
            return getContextValue(field) != "true"
        }

        return getContextValue(expr) == "true"
    }

    private fun compareValues(left: String, right: String, op: String): Boolean {
        val leftVal = getContextValue(left)

        val rightVal = if (right.startsWith("\"") && right.endsWith("\"")) {
            right.removeSurrounding("\"")
        } else {
            getContextValue(right).ifEmpty { right }
        }

        val leftNum = leftVal.toIntOrNull()
        val rightNum = rightVal.toIntOrNull()

        if (leftNum != null && rightNum != null) {
            return when (op) {
                "==" -> leftNum == rightNum
                "!=" -> leftNum != rightNum
                "<" -> leftNum < rightNum
                ">" -> leftNum > rightNum
                "<=" -> leftNum <= rightNum
                ">=" -> rightNum >= rightNum
                else -> false
            }
        }

        return when (op) {
            "==" -> leftVal == rightVal
            "!=" -> leftVal != rightVal
            else -> false
        }
    }

    private fun getContextValue(field: String): String {
        if (customVariables.containsKey(field)) {
            return customVariables[field] ?: ""
        }

        return when (field) {
            "hour" -> ctx.hour.toString()
            "minute" -> ctx.minute.toString()
            "month" -> ctx.month.toString()
            "day" -> ctx.day.toString()
            "dayOfWeek" -> ctx.dayOfWeek
            "isWeekend" -> ctx.isWeekend.toString()
            "isHoliday" -> ctx.isHoliday.toString()
            "holidayName" -> ctx.holidayName ?: ""
            "season" -> ctx.season
            "isSpecialDay" -> ctx.isSpecialDay.toString()
            "specialDayName" -> ctx.specialDayName ?: ""
            "weatherCode" -> ctx.weatherCode
            "weatherEmoji" -> ctx.weatherEmoji
            "temperature" -> ctx.temperature.toString()
            "temperatureFeeling" -> ctx.temperatureFeeling
            "humidity" -> ctx.humidity?.toString() ?: "0"
            "batteryLevel" -> ctx.batteryLevel.toString()
            "batteryStatus" -> ctx.batteryStatus
            "isCharging" -> ctx.isCharging.toString()
            "isLowBattery" -> ctx.isLowBattery.toString()
            "launchCount" -> ctx.launchCount.toString()
            "lastLaunchHoursAgo" -> ctx.lastLaunchHoursAgo?.toString() ?: "0"
            "isFirstLaunchToday" -> ctx.isFirstLaunchToday.toString()
            "consecutiveDays" -> ctx.consecutiveDays.toString()
            "userName" -> ctx.userName
            "userGender" -> ctx.userGender ?: ""
            "isNearBedtime" -> ctx.isNearBedtime.toString()
            "isNearWakeup" -> ctx.isNearWakeup.toString()
            "moonPhase" -> ctx.moonPhase ?: ""
            "timeSlot" -> ctx.timeSlot
            else -> ""
        }
    }
}