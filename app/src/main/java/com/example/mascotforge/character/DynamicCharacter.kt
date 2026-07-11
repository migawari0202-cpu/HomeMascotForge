package com.example.mascotforge.character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.character.speech.OperationType
import com.example.mascotforge.character.speech.TagParser
import com.example.mascotforge.character.speech.VariableOperation
import com.example.mascotforge.speech.SpeechContext
import java.io.File

/**
 * 動的生成されたキャラクター
 *
 * 改善点:
 * - customVariables を map 化して O(1) 参照
 * - 変数操作を専用関数へ分離
 * - Context は applicationContext を保持
 * - TagParser / CharacterStateManager を使い回し
 * - セリフ選択ロジックを整理
 * - ログとフォールバックを明確化
 */
class DynamicCharacter(
    private val charId: String,
    private val metadata: CharacterMetadata,
    private var speeches: Map<String, List<String>>,
    context: Context,
    private val source: CharacterSource,
    private val loader: SafeCharacterLoader? = null
) : CharacterProvider {

    companion object {
        private const val TAG = "DynamicCharacter"
        private const val TRIGGER_PREFS = "dynamic_char_triggers"
        private const val KEY_LAST_CONSECUTIVE_DAYS = "last_consecutive_days_"
        /** SharedPreferences 未記録時のセンチネル（初回はベースラインのみ保存して発火しない） */
        private const val UNSET_CONSECUTIVE_DAYS = Int.MIN_VALUE
    }

    private val appContext: Context = context.applicationContext

    override val id: String = metadata.id
    override val name: String = metadata.name

    private val customVarDefsByName: Map<String, CustomVariable> =
        metadata.customVariables.associateBy { it.name }

    private val variableManager = CustomVariableManager(
        context = appContext,
        characterId = charId,
        variableDefinitions = metadata.customVariables
    )

    private val stateManager by lazy {
        CharacterStateManager(appContext)
    }

    private val triggerPrefs by lazy {
        appContext.getSharedPreferences(TRIGGER_PREFS, Context.MODE_PRIVATE)
    }

    @Volatile
    private var lastTimeSlot: String? = null

    @Volatile
    private var extractedEmotionKey: String? = null

    @Volatile
    private var cachedShell: CachedShell? = null

    private data class CachedShell(
        val shellId: String?,
        val shell: Shell?
    )

    private var currentSpeechFile: String? = null
    private var cachedSpeeches: List<String> = emptyList()
    private val shownSpeeches = mutableSetOf<String>()

    override suspend fun getSpeech(ctx: SpeechContext): String? {
        // 環境系トリガー（起動・連続日数変化・時間帯）はセリフ有無に関係なく評価
        fireEnvironmentTriggers(ctx)

        val speeches = getSpeeches(ctx)
        if (speeches.isEmpty()) {
            Log.w(TAG, "[$charId] No speeches available")
            extractedEmotionKey = null
            return null
        }

        val template = pickSpeechTemplate(speeches, ctx) ?: return null

        val parsedSpeech = createTagParser().parseAndApply(template)
        extractedEmotionKey = parsedSpeech.emotion

        applyVariableOperations(parsedSpeech.variableOperations)

        if (parsedSpeech.variableOperations.isNotEmpty()) {
            Log.d(TAG, "[$charId] Variable operations applied: ${parsedSpeech.variableOperations.size}")
        }

        val expandedSpeech = expandVariables(parsedSpeech.cleanedText, ctx)

        // onSpeech: セリフが実際に表示されるときだけ発火（タグ操作の後）
        fireOnSpeechTrigger(ctx)

        Log.d(TAG, "[$charId] Speech: $expandedSpeech")
        return expandedSpeech
    }

    /**
     * 起動・連続日数・時間帯など、セリフ本文の有無に依存しないトリガー。
     */
    private fun fireEnvironmentTriggers(ctx: SpeechContext) {
        if (ctx.isFirstLaunchToday) {
            Log.d(TAG, "[$charId] First launch today - triggering ON_LAUNCH")
            variableManager.triggerRules(
                trigger = CustomVariable.ChangeRule.Trigger.ON_LAUNCH,
                speechContext = ctx
            )
        }

        fireOnConsecutiveDaysIfChanged(ctx)

        if (lastTimeSlot != null && lastTimeSlot != ctx.timeSlot) {
            Log.d(TAG, "[$charId] TimeSlot changed: $lastTimeSlot -> ${ctx.timeSlot}")
            variableManager.triggerRules(
                trigger = CustomVariable.ChangeRule.Trigger.ON_TIME_SLOT_CHANGE,
                speechContext = ctx
            )
        }

        lastTimeSlot = ctx.timeSlot
    }

    /**
     * 連続起動日数が前回観測値から変化したときだけ ON_CONSECUTIVE_DAYS を発火する。
     * プロセス再起動をまたいでも正しく検知するため SharedPreferences に前回値を保持する。
     * 初回観測時はベースラインとして保存するのみ（「変化」ではない）。
     */
    private fun fireOnConsecutiveDaysIfChanged(ctx: SpeechContext) {
        val key = KEY_LAST_CONSECUTIVE_DAYS + charId
        val lastConsecutive = triggerPrefs.getInt(key, UNSET_CONSECUTIVE_DAYS)
        val current = ctx.consecutiveDays

        if (lastConsecutive == UNSET_CONSECUTIVE_DAYS) {
            triggerPrefs.edit().putInt(key, current).apply()
            Log.d(TAG, "[$charId] Baseline consecutiveDays=$current (ON_CONSECUTIVE_DAYS not fired)")
            return
        }

        if (lastConsecutive != current) {
            Log.d(TAG, "[$charId] consecutiveDays changed: $lastConsecutive -> $current - triggering ON_CONSECUTIVE_DAYS")
            variableManager.triggerRules(
                trigger = CustomVariable.ChangeRule.Trigger.ON_CONSECUTIVE_DAYS,
                speechContext = ctx
            )
            triggerPrefs.edit().putInt(key, current).apply()
        }
    }

    /** セリフ表示時トリガー */
    private fun fireOnSpeechTrigger(ctx: SpeechContext) {
        Log.d(TAG, "[$charId] Speech displayed - triggering ON_SPEECH")
        variableManager.triggerRules(
            trigger = CustomVariable.ChangeRule.Trigger.ON_SPEECH,
            speechContext = ctx
        )
    }

    private fun pickSpeechTemplate(speeches: List<String>, ctx: SpeechContext): String? {
        if (speeches.isEmpty()) return null

        val unshown = speeches.filterNot { it in shownSpeeches }

        val selected = when {
            unshown.isNotEmpty() -> unshown.random()
            else -> {
                shownSpeeches.clear()
                speeches.random()
            }
        }

        shownSpeeches += selected

        if (unshown.size <= 1) {
            currentSpeechFile = null
        }

        return selected
    }

    private fun createTagParser(): TagParser {
        return TagParser(
            characterState = variableManager.getCharacterState(),
            stateManager = stateManager,
            characterId = charId
        )
    }

    private fun getSpeeches(ctx: SpeechContext): List<String> {
        if (metadata.speechRules.isNotEmpty()) {
            if (loader == null) {
                Log.w(TAG, "[$charId] speechRules exists but loader is null. Falling back to legacy speeches.")
            } else {
                val selectedFile = selectSpeechFile(ctx, metadata.speechRules)
                if (selectedFile == null) {
                    Log.w(TAG, "[$charId] No matching speech file found")
                    return emptyList()
                }

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
        }

        return legacySpeechesFor(ctx)
    }

    private fun legacySpeechesFor(ctx: SpeechContext): List<String> {
        speeches["current"]?.let { return it }
        return speeches[ctx.timeSlot] ?: speeches["morning"] ?: emptyList()
    }

    private fun selectSpeechFile(context: SpeechContext, rules: List<SpeechRule>): String? {
        val defaultRule = rules.firstOrNull { it.conditions.isEmpty() && it.anyOf.isEmpty() }
        val conditionalRules = rules.filter { it.conditions.isNotEmpty() || it.anyOf.isNotEmpty() }

        val customValues = variableManager.getAllValues(metadata.customVariables)

        conditionalRules
            .sortedByDescending { it.priority }
            .forEach { rule ->
                val allOfMatches = rule.conditions.isEmpty() || context.matchesAll(rule.conditions, customValues)
                val anyOfMatches = rule.anyOf.isEmpty() || context.matchesAny(rule.anyOf, customValues)

                if (allOfMatches && anyOfMatches) {
                    val selectedFile = rule.files.randomOrNull()
                    if (selectedFile != null) {
                        Log.d(
                            TAG,
                            "[$charId] Matched rule (priority ${rule.priority}): $selectedFile" +
                                if (rule.files.size > 1) " (${rule.files.size} candidates)" else ""
                        )
                        return selectedFile
                    }
                }
            }

        defaultRule?.files?.randomOrNull()?.let {
            Log.d(TAG, "[$charId] Using default rule: $it")
            return it
        }

        Log.w(TAG, "[$charId] No matching rule found")
        return null
    }

    private fun applyVariableOperations(operations: List<VariableOperation>) {
        operations.forEach { op ->

            val varDef = customVarDefsByName[op.varName] ?: run {
                Log.w(TAG, "[$charId] [var:] '${op.varName}' is undefined; skipping")
                return@forEach
            }

            when (op.type) {
                OperationType.SET_STRING -> applySetString(varDef, op)
                OperationType.TOGGLE -> applyToggle(varDef, op)
                OperationType.ADD,
                OperationType.SUBTRACT,
                OperationType.MULTIPLY,
                OperationType.DIVIDE,
                OperationType.SET -> applyNumericOperation(varDef, op)
                else -> {
                    Log.w(TAG, "[$charId] [var:] unsupported operation: ${op.type}")
                }
            }
        }
    }

    private fun applySetString(varDef: CustomVariable, op: VariableOperation) {
        val strVal = op.stringValue ?: return

        when (varDef.type) {
            CustomVariable.VariableType.STRING -> {
                if (varDef.options != null && strVal !in varDef.options) {
                    Log.w(TAG, "[$charId] [var:] '$strVal' is outside options of '${op.varName}'")
                    return
                }
                variableManager.setValue(op.varName, strVal)
                Log.d(TAG, "[$charId] [var:] ${op.varName} = \"$strVal\"")
            }

            CustomVariable.VariableType.BOOLEAN -> {
                val boolVal = strVal.toBooleanStrictOrNull()
                if (boolVal == null) {
                    Log.w(TAG, "[$charId] [var:] invalid boolean string: '$strVal'")
                    return
                }
                variableManager.setValue(op.varName, boolVal)
                Log.d(TAG, "[$charId] [var:] ${op.varName} = $boolVal")
            }

            CustomVariable.VariableType.NUMBER -> {
                Log.w(TAG, "[$charId] [var:] NUMBER variable '${op.varName}' cannot use SET_STRING")
            }
        }
    }

    private fun applyToggle(varDef: CustomVariable, op: VariableOperation) {
        if (varDef.type != CustomVariable.VariableType.BOOLEAN) {
            Log.w(TAG, "[$charId] [var:] TOGGLE is only valid for BOOLEAN ('${op.varName}' is ${varDef.type})")
            return
        }

        val current = variableManager.getValue(op.varName) as? Boolean ?: false
        val next = !current
        variableManager.setValue(op.varName, next)
        Log.d(TAG, "[$charId] [var:] ${op.varName}: $current -> $next (TOGGLE)")
    }

    private fun applyNumericOperation(varDef: CustomVariable, op: VariableOperation) {
        if (varDef.type != CustomVariable.VariableType.NUMBER) {
            Log.w(TAG, "[$charId] [var:] '${op.varName}' is not NUMBER, skipping ${op.type}")
            return
        }

        val current = when (val value = variableManager.getValue(op.varName)) {
            is Int -> value
            is Long -> value.toInt()
            is Float -> value.toInt()
            is Double -> value.toInt()
            else -> {
                Log.w(TAG, "[$charId] [var:] '${op.varName}' has no numeric current value")
                return
            }
        }

        val next = when (op.type) {
            OperationType.ADD -> current + op.operationValue
            OperationType.SUBTRACT -> current - op.operationValue
            OperationType.MULTIPLY -> current * op.operationValue
            OperationType.DIVIDE -> if (op.operationValue != 0) current / op.operationValue else current
            OperationType.SET -> op.operationValue
            else -> current
        }

        variableManager.setValue(op.varName, next)
        Log.d(TAG, "[$charId] [var:] ${op.varName}: $current -> $next (${op.type} ${op.operationValue})")
    }

    override fun getCharaImage(ctx: SpeechContext): Bitmap? {
        var emotion = "normal"
        val shell = resolveActiveShell()

        if (extractedEmotionKey != null) {
            val knownInCharacter = metadata.imageMapping.containsKey(extractedEmotionKey)
            val knownInShell = shell?.emotionMapping?.containsKey(extractedEmotionKey) == true

            emotion = when {
                knownInCharacter || knownInShell -> extractedEmotionKey!!
                else -> {
                    Log.w(
                        TAG,
                        "[$charId] Unknown emotion tag: '$extractedEmotionKey'. " +
                            "Character: ${metadata.imageMapping.keys.joinToString()}, " +
                            "Shell: ${shell?.emotionMapping?.keys?.joinToString() ?: "none"}. " +
                            "Falling back to emotionRules."
                    )
                    determineEmotion(ctx)
                }
            }
        } else {
            emotion = determineEmotion(ctx)
        }

        extractedEmotionKey = null

        shell?.emotionMapping?.get(emotion)?.let { shellFileName ->
            loadShellImage(shell.id, shellFileName)?.let {
                Log.d(TAG, "[$charId] Using shell image: ${shell.id}/$shellFileName")
                return it
            }
            Log.w(TAG, "[$charId] Shell image not found, falling back to character image.")
        }

        val imageFileName =
            metadata.imageMapping[emotion]
                ?: metadata.imageMapping["normal"]
                ?: metadata.imageMapping.values.firstOrNull()
                ?: "character.png"

        val imagePath = "${source.basePath}/images/$imageFileName"
        return loadImage(imagePath = imagePath, tag = emotion)
    }

    private fun resolveActiveShell(): Shell? {
        val prefs = appContext.getSharedPreferences("prefs_shells", Context.MODE_PRIVATE)
        val currentShellId = prefs.getString("active_shell_$charId", null)

        cachedShell?.let { cached ->
            if (cached.shellId == currentShellId) return cached.shell
        }

        val loaded = currentShellId?.let { ShellRegistry.loadShell(appContext, it) }
        cachedShell = CachedShell(currentShellId, loaded)
        return loaded
    }

    private fun loadShellImage(shellId: String, fileName: String): Bitmap? {
        if (fileName.contains("..") || fileName.contains('/') || fileName.contains('\\')) {
            Log.e(TAG, "[$charId] Invalid shell image filename: $fileName")
            return null
        }

        val cacheKey = "shell:$shellId:$fileName"
        CharacterBitmapCache.get(cacheKey)?.let { return it }

        return try {
            val file = File(appContext.filesDir, "shells/$shellId/images/$fileName")
            if (!file.isFile) {
                Log.w(TAG, "[$charId] Shell image not found: ${file.path}")
                return null
            }

            BitmapFactory.decodeFile(file.absolutePath)
                ?.also { CharacterBitmapCache.put(cacheKey, it) }
        } catch (e: Exception) {
            Log.w(TAG, "[$charId] Failed to load shell image: $shellId/$fileName", e)
            null
        }
    }

    private fun determineEmotion(ctx: SpeechContext): String {
        var defaultEmotion: String? = null

        for (rule in metadata.emotionRules) {
            if (rule.isDefault) {
                defaultEmotion = rule.emotion
                continue
            }

            val condition = rule.condition ?: continue
            if (evaluateCondition(condition, ctx)) {
                return rule.emotion
            }
        }

        return defaultEmotion ?: "normal"
    }

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

    private fun expandVariables(template: String, ctx: SpeechContext): String {
        var result = template

        val standardVariables = mapOf(
            "userName" to ctx.userName,
            "userGender" to (ctx.userGender ?: ""),
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
            "humidity" to (ctx.humidity?.toString() ?: ""),
            "batteryLevel" to ctx.batteryLevel.toString(),
            "batteryStatus" to ctx.batteryStatus,
            "isCharging" to ctx.isCharging.toString(),
            "isLowBattery" to ctx.isLowBattery.toString(),
            "season" to ctx.season,
            "isSpecialDay" to ctx.isSpecialDay.toString(),
            "specialDayName" to (ctx.specialDayName ?: ""),
            "holidayName" to (ctx.holidayName ?: ""),
            "isWeekend" to ctx.isWeekend.toString(),
            "isHoliday" to ctx.isHoliday.toString(),
            "isNearBedtime" to ctx.isNearBedtime.toString(),
            "isNearWakeup" to ctx.isNearWakeup.toString(),
            "moonPhase" to (ctx.moonPhase ?: ""),
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

        for ((key, value) in standardVariables) {
            result = result.replace("{$key}", value)
        }

        val customValues = variableManager.getAllValues(metadata.customVariables)
        for ((key, value) in customValues) {
            result = result.replace("{$key}", value)
        }

        // 三項演算子 {条件 ? 真値 : 偽値} を評価
        result = expandTernaryExpressions(result, ctx, customValues)

        return result
    }

    /**
     * セリフ内の三項演算子 {条件 ? 真値 : 偽値} を評価して置換する。
     *
     * 例: {temperatureFeeling == "cold" ? 寒いね : いい天気だね}
     *     条件部には標準変数・カスタム変数・文字列リテラルが使用可能。
     */
    private fun expandTernaryExpressions(
        template: String,
        ctx: SpeechContext,
        customValues: Map<String, String>
    ): String {
        // {条件 ? 真 : 偽} のパターン（条件部は ? を含まない前提）
        val ternaryRegex = "\\{([^?}]+)\\?\\s*([^:}]+)\\s*:\\s*([^}]+)\\}".toRegex()

        return ternaryRegex.replace(template) { matchResult ->
            val condition = matchResult.groupValues[1].trim()
            val trueBranch = matchResult.groupValues[2].trim()
            val falseBranch = matchResult.groupValues[3].trim()

            val result = try {
                val evaluator = SafeExpressionEvaluator(ctx, customValues)
                if (evaluator.evaluate(condition)) trueBranch else falseBranch
            } catch (e: Exception) {
                Log.w(TAG, "[$charId] Ternary evaluation failed: $condition", e)
                falseBranch
            }

            result
        }
    }

    private fun loadImage(imagePath: String, tag: String?): Bitmap? {
        if (imagePath.contains("..") || !imagePath.startsWith(source.basePath)) {
            Log.e(TAG, "[$charId] Invalid image path: $imagePath")
            return null
        }

        val settings = CharacterSettingsLoader.load(appContext, source)
        val cutoutSpec = settings?.findCutoutSpec(tag)
        val tolerance = cutoutSpec?.tolerance ?: settings?.imageCutout?.defaultTolerance
        val colors = cutoutSpec?.colors.orEmpty()

        val cacheKey = when {
            colors.isEmpty() || tolerance == null -> "$charId:$imagePath:raw"
            else -> "$charId:$imagePath:cutout:$tag:t$tolerance:${
                colors.joinToString(",") { it.toUInt().toString(16) }
            }"
        }

        CharacterBitmapCache.get(cacheKey)?.let { return it }

        return try {
            val bitmap = when (source) {
                is CharacterSource.Assets -> {
                    appContext.assets.open(imagePath).use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }

                is CharacterSource.InstalledFiles -> {
                    val file = File(appContext.filesDir, imagePath)
                    if (!file.isFile) {
                        Log.w(TAG, "[$charId] Image file not found: ${file.path}")
                        null
                    } else {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                }
            }

            bitmap?.let { raw ->
                val processed = if (colors.isEmpty() || tolerance == null) {
                    raw
                } else {
                    applyColorCutout(raw, colors, tolerance.coerceAtLeast(0))
                }
                CharacterBitmapCache.put(cacheKey, processed)
                processed
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$charId] Failed to load image: $imagePath", e)
            null
        }
    }

    private fun applyColorCutout(src: Bitmap, targetColors: List<Int>, tolerance: Int): Bitmap {
        if (targetColors.isEmpty()) return src

        val targets = targetColors.map {
            Triple((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF)
        }

        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        val toleranceSq = tolerance * tolerance

        for (i in pixels.indices) {
            val p = pixels[i]
            if ((p ushr 24) == 0) continue

            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            var hit = false
            for (t in targets) {
                val dr = r - t.first
                val dg = g - t.second
                val db = b - t.third
                if (dr * dr + dg * dg + db * db <= toleranceSq) {
                    hit = true
                    break
                }
            }

            if (hit) {
                pixels[i] = Color.TRANSPARENT
            }
        }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        if (!src.isRecycled) src.recycle()
        return result
    }

    override fun isAvailable(): Boolean = true

    fun triggerTouchRules(ctx: SpeechContext) {
        Log.d(TAG, "[$charId] triggerTouchRules: firing ON_TOUCH rules")
        variableManager.triggerRules(
            trigger = CustomVariable.ChangeRule.Trigger.ON_TOUCH,
            speechContext = ctx
        )
    }

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