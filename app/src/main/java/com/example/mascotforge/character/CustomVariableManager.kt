package com.example.mascotforge.character

import android.content.Context
import android.util.Log
import com.example.mascotforge.speech.SpeechContext

/**
 * カスタム変数の管理・永続化（CharacterState.customVars 統合版）
 *
 * 統合の仕組み:
 * - character.json で定義された順番に customVars[0-19] にマッピング
 * - 型変換を自動実行（Int ↔ Boolean/String）
 * - CharacterStateManager を使用して永続化
 * - TagParser と同じデータを共有
 */
class CustomVariableManager(
    private val context: Context,
    val characterId: String,  // ← private を削除（拡張関数でアクセスするため）
    private val variableDefinitions: List<CustomVariable>
) {
    companion object {
        private const val TAG = "CustomVariableManager"
        private const val MAX_VARIABLES = 30
        private const val INIT_PREFS_NAME = "custom_var_init"
    }

    private val stateManager = CharacterStateManager(context)
    private val initPrefs = context.getSharedPreferences(INIT_PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // 変数が20個を超えていないかチェック
        require(variableDefinitions.size <= MAX_VARIABLES) {
            "Too many custom variables: ${variableDefinitions.size} (max: $MAX_VARIABLES)"
        }

        // 初回起動時に初期値を設定
        initializeIfNeeded()
    }

    /**
     * 初回起動時に全変数を初期値で初期化。
     * 初期化済みかどうかは専用の SharedPreferences フラグで管理する。
     * これにより initialValue == 0 の変数も正しく初期化される。
     */
    private fun initializeIfNeeded() {
        val initKey = "initialized_$characterId"
        if (initPrefs.getBoolean(initKey, false)) return

        Log.d(TAG, "[$characterId] Initializing custom variables for the first time")
        val state = stateManager.getState(characterId)
        variableDefinitions.forEachIndexed { index, variable ->
            val rawValue = convertToRaw(variable, variable.initialValue)
            state.setCustomVar(variable.name, rawValue)
        }
        stateManager.saveState(characterId, state)
        initPrefs.edit().putBoolean(initKey, true).apply()
    }

    /**
     * 変数の現在値を取得（型変換済み）
     */
    fun getValue(variable: CustomVariable): Any {
        val state = stateManager.getState(characterId)
        val rawValue = state.getCustomVar(variable.name)
        return convertFromRaw(variable, rawValue)
    }

    /**
     * 変数名から値を取得
     */
    fun getValue(variableName: String): Any? {
        val variable = variableDefinitions.find { it.name == variableName } ?: return null
        return getValue(variable)
    }

    /**
     * 変数の値を設定（型変換 + 範囲制限）
     */
    fun setValue(variable: CustomVariable, value: Any) {
        val rawValue = convertToRaw(variable, value)
        val state = stateManager.getState(characterId)
        state.setCustomVar(variable.name, rawValue)
        stateManager.saveState(characterId, state)
        Log.d(TAG, "[$characterId] ${variable.name} = $value (raw: $rawValue)")
    }

    /**
     * 変数名から値を設定
     */
    fun setValue(variableName: String, value: Any): Boolean {
        val variable = variableDefinitions.find { it.name == variableName } ?: return false
        setValue(variable, value)
        return true
    }

    /**
     * 型付き値 → 整数値に変換
     */
    private fun convertToRaw(variable: CustomVariable, value: Any): Int {
        return when (variable.type) {
            CustomVariable.VariableType.NUMBER -> {
                var numValue = when (value) {
                    is Int -> value
                    is String -> value.toIntOrNull() ?: (variable.initialValue as Int)
                    else -> variable.initialValue as Int
                }

                // 範囲制限
                if (variable.min != null && numValue < variable.min) {
                    numValue = variable.min
                    Log.w(TAG, "[$characterId] ${variable.name}: value clamped to min ($numValue)")
                }
                if (variable.max != null && numValue > variable.max) {
                    numValue = variable.max
                    Log.w(TAG, "[$characterId] ${variable.name}: value clamped to max ($numValue)")
                }

                numValue
            }

            CustomVariable.VariableType.BOOLEAN -> {
                val boolValue = when (value) {
                    is Boolean -> value
                    is Int -> value != 0
                    is String -> value.toBoolean()
                    else -> variable.initialValue as Boolean
                }
                if (boolValue) 1 else 0
            }

            CustomVariable.VariableType.STRING -> {
                val strValue = value.toString()
                // 型を明示的に指定
                val options: List<String> = variable.options ?: emptyList()

                if (options.isEmpty()) {
                    Log.w(TAG, "[$characterId] ${variable.name}: no options defined")
                    return 0
                }

                // 文字列 → インデックスに変換
                val index = options.indexOf(strValue)
                if (index == -1) {
                    Log.w(TAG, "[$characterId] ${variable.name}: '$strValue' not in options, using first option")
                    0
                } else {
                    index
                }
            }
        }
    }

    /**
     * 整数値 → 型付き値に変換
     */
    private fun convertFromRaw(variable: CustomVariable, rawValue: Int): Any {
        return when (variable.type) {
            CustomVariable.VariableType.NUMBER -> rawValue

            CustomVariable.VariableType.BOOLEAN -> rawValue != 0

            CustomVariable.VariableType.STRING -> {
                // 型を明示的に指定
                val options: List<String> = variable.options ?: emptyList()
                if (options.isEmpty()) {
                    Log.w(TAG, "[$characterId] ${variable.name}: no options defined")
                    return ""
                }

                // インデックス範囲チェック
                if (rawValue < 0 || rawValue >= options.size) {
                    Log.w(TAG, "[$characterId] ${variable.name}: index $rawValue out of range, using first option")
                    options.first()
                } else {
                    options[rawValue]
                }
            }
        }
    }

    /**
     * 変数を更新（ルールに基づく）
     */
    fun applyRule(
        variable: CustomVariable,
        rule: CustomVariable.ChangeRule,
        speechContext: SpeechContext
    ) {
        // 条件チェック
        if (rule.condition != null) {
            val customValues = getAllValues(variableDefinitions)
            val evaluator = SafeExpressionEvaluator(speechContext, customValues)

            if (!evaluator.evaluate(rule.condition)) {
                Log.d(TAG, "[$characterId] Rule condition not met: ${rule.condition}")
                return
            }
        }

        val currentValue = getValue(variable)
        Log.d(TAG, "[$characterId] Applying rule: ${variable.name} (current: $currentValue)")

        val newValue = when (rule.action) {
            CustomVariable.ChangeRule.Action.SET -> {
                rule.value ?: currentValue
            }
            CustomVariable.ChangeRule.Action.INCREMENT -> {
                if (variable.type == CustomVariable.VariableType.NUMBER) {
                    val increment = (rule.value as? Int) ?: 1
                    val currentInt = currentValue as? Int ?: run {
                        Log.e(TAG, "[$characterId] Expected NUMBER for INCREMENT, got ${currentValue::class.simpleName}")
                        return
                    }
                    currentInt + increment
                } else {
                    currentValue
                }
            }
            CustomVariable.ChangeRule.Action.DECREMENT -> {
                if (variable.type == CustomVariable.VariableType.NUMBER) {
                    val decrement = (rule.value as? Int) ?: 1
                    val currentInt = currentValue as? Int ?: run {
                        Log.e(TAG, "[$characterId] Expected NUMBER for DECREMENT, got ${currentValue::class.simpleName}")
                        return
                    }
                    currentInt - decrement
                } else {
                    currentValue
                }
            }
            CustomVariable.ChangeRule.Action.TOGGLE -> {
                if (variable.type == CustomVariable.VariableType.BOOLEAN) {
                    val currentBool = currentValue as? Boolean ?: run {
                        Log.e(TAG, "[$characterId] Expected BOOLEAN for TOGGLE, got ${currentValue::class.simpleName}")
                        return
                    }
                    !currentBool
                } else {
                    currentValue
                }
            }
        }

        setValue(variable, newValue)
        Log.d(TAG, "[$characterId] Rule applied: ${variable.name} = $newValue")
    }

    /**
     * 特定のトリガーに対応するルールを実行
     */
    fun triggerRules(
        trigger: CustomVariable.ChangeRule.Trigger,
        speechContext: SpeechContext
    ) {
        Log.d(TAG, "[$characterId] Triggering rules: $trigger")

        variableDefinitions.forEach { variable ->
            variable.changeRules
                .filter { it.trigger == trigger }
                .forEach { rule ->
                    applyRule(variable, rule, speechContext)
                }
        }
    }

    /**
     * 全変数をリセット（初期値に戻す）
     */
    fun resetAll() {
        Log.d(TAG, "[$characterId] Resetting all variables")

        val state = stateManager.getState(characterId)
        variableDefinitions.forEach { variable ->
            val rawValue = convertToRaw(variable, variable.initialValue)
            state.setCustomVar(variable.name, rawValue)
        }
        stateManager.saveState(characterId, state)
    }

    /**
     * 変数の値をマップで取得（セリフ展開用）
     */
    fun getAllValues(variables: List<CustomVariable>): Map<String, String> {
        return variables.associate { variable ->
            variable.name to getValue(variable).toString()
        }
    }

    /**
     * デバッグ用：全変数の状態をログ出力
     */
    fun logAllValues() {
        Log.d(TAG, "=== Custom Variables for $characterId ===")
        variableDefinitions.forEach { variable ->
            val value = getValue(variable)
            val rawValue = stateManager.getState(characterId).getCustomVar(variable.name)
            Log.d(TAG, "${variable.name} (${variable.type}): $value (raw: $rawValue)")
        }
    }

    /**
     * CharacterState を直接取得（TagParser連携用）
     */
    fun getCharacterState(): CharacterState {
        return stateManager.getState(characterId)
    }

    /**
     * CharacterState を保存
     */
    fun saveCharacterState() {
        val state = stateManager.getState(characterId)
        stateManager.saveState(characterId, state)
    }

    /**
     * Context を取得（拡張関数用）
     */
    fun getContext(): Context = context
}

/**
 * 拡張関数: CustomVariableManager と TagParser を連携
 */
fun CustomVariableManager.createTagParser(): com.example.mascotforge.character.speech.TagParser {
    val stateManager = CharacterStateManager(getContext())
    return com.example.mascotforge.character.speech.TagParser(
        characterState = getCharacterState(),
        stateManager = stateManager,
        characterId = characterId  // ← 今度はアクセスできる（public にした）
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 使用例
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * 使用例1: 基本的な使い方
 */
fun exampleBasicUsage(context: Context, metadata: CharacterMetadata) {
    val varManager = CustomVariableManager(
        context = context,
        characterId = metadata.id,
        variableDefinitions = metadata.customVariables
    )

    // 変数の取得
    val friendship = varManager.getValue("friendship") as? Int
    println("Friendship: $friendship")

    // 変数の設定
    varManager.setValue("friendship", 75)

    // 全変数を表示
    varManager.logAllValues()
}

/**
 * 使用例2: TagParserとの連携
 */
fun exampleWithTagParser(context: Context, metadata: CharacterMetadata) {
    val varManager = CustomVariableManager(
        context = context,
        characterId = metadata.id,
        variableDefinitions = metadata.customVariables
    )

    // TagParserを作成
    val parser = varManager.createTagParser()

    // セリフを解析して変数操作
    val speech = "ありがとう！[var: customVars[0] + 5]"
    val result = parser.parseAndApply(speech)

    println("Speech: ${result.cleanedText}")

    // 変数の変化を確認
    varManager.logAllValues()
}

/**
 * 使用例3: ルールの実行
 */
fun exampleWithRules(context: Context, metadata: CharacterMetadata, speechContext: SpeechContext) {
    val varManager = CustomVariableManager(
        context = context,
        characterId = metadata.id,
        variableDefinitions = metadata.customVariables
    )

    // 起動時のルールを実行
    varManager.triggerRules(
        trigger = CustomVariable.ChangeRule.Trigger.ON_LAUNCH,
        speechContext = speechContext
    )

    // セリフ表示時のルールを実行
    varManager.triggerRules(
        trigger = CustomVariable.ChangeRule.Trigger.ON_SPEECH,
        speechContext = speechContext
    )
}