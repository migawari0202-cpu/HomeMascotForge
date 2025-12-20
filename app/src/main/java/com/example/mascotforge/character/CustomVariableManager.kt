package com.mascotforge.character

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mascotforge.speech.SpeechContext

/**
 * カスタム変数の管理・永続化
 *
 * SharedPreferencesを使用してキャラごとに変数を保存
 */
class CustomVariableManager(
    private val context: Context,
    private val characterId: String
) {
    companion object {
        private const val TAG = "CustomVariableManager"
        private const val PREFS_PREFIX = "custom_vars_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "${PREFS_PREFIX}${characterId}",
        Context.MODE_PRIVATE
    )

    /**
     * 変数の現在値を取得
     */
    fun getValue(variable: CustomVariable): Any {
        val key = variable.name

        return when (variable.type) {
            CustomVariable.VariableType.NUMBER -> {
                prefs.getInt(key, variable.initialValue as Int)
            }
            CustomVariable.VariableType.BOOLEAN -> {
                prefs.getBoolean(key, variable.initialValue as Boolean)
            }
            CustomVariable.VariableType.STRING -> {
                prefs.getString(key, variable.initialValue as String) ?: variable.initialValue
            }
        }
    }

    /**
     * 変数の値を設定
     */
    fun setValue(variable: CustomVariable, value: Any) {
        val key = variable.name

        when (variable.type) {
            CustomVariable.VariableType.NUMBER -> {
                var numValue = when (value) {
                    is Int -> value
                    is String -> value.toIntOrNull() ?: (variable.initialValue as Int)
                    else -> variable.initialValue as Int
                }

                // 範囲制限
                if (variable.min != null && numValue < variable.min) {
                    numValue = variable.min
                }
                if (variable.max != null && numValue > variable.max) {
                    numValue = variable.max
                }

                prefs.edit().putInt(key, numValue).apply()
                Log.d(TAG, "[$characterId] $key = $numValue")
            }
            CustomVariable.VariableType.BOOLEAN -> {
                val boolValue = when (value) {
                    is Boolean -> value
                    is String -> value.toBoolean()
                    else -> variable.initialValue as Boolean
                }
                prefs.edit().putBoolean(key, boolValue).apply()
                Log.d(TAG, "[$characterId] $key = $boolValue")
            }
            CustomVariable.VariableType.STRING -> {
                val strValue = value.toString()
                prefs.edit().putString(key, strValue).apply()
                Log.d(TAG, "[$characterId] $key = $strValue")
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
            val evaluator = SafeExpressionEvaluator(speechContext)
            if (!evaluator.evaluate(rule.condition)) {
                return // 条件不一致、実行しない
            }
        }

        val currentValue = getValue(variable)

        val newValue = when (rule.action) {
            CustomVariable.ChangeRule.Action.SET -> {
                rule.value ?: currentValue
            }
            CustomVariable.ChangeRule.Action.INCREMENT -> {
                if (variable.type == CustomVariable.VariableType.NUMBER) {
                    val increment = (rule.value as? Int) ?: 1
                    (currentValue as Int) + increment
                } else {
                    currentValue
                }
            }
            CustomVariable.ChangeRule.Action.DECREMENT -> {
                if (variable.type == CustomVariable.VariableType.NUMBER) {
                    val decrement = (rule.value as? Int) ?: 1
                    (currentValue as Int) - decrement
                } else {
                    currentValue
                }
            }
            CustomVariable.ChangeRule.Action.TOGGLE -> {
                if (variable.type == CustomVariable.VariableType.BOOLEAN) {
                    !(currentValue as Boolean)
                } else {
                    currentValue
                }
            }
        }

        setValue(variable, newValue)
    }

    /**
     * 特定のトリガーに対応するルールを実行
     */
    fun triggerRules(
        variables: List<CustomVariable>,
        trigger: CustomVariable.ChangeRule.Trigger,
        speechContext: SpeechContext
    ) {
        variables.forEach { variable ->
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
    fun resetAll(variables: List<CustomVariable>) {
        variables.forEach { variable ->
            setValue(variable, variable.initialValue)
        }
        Log.d(TAG, "[$characterId] All variables reset")
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
    fun logAllValues(variables: List<CustomVariable>) {
        Log.d(TAG, "=== Custom Variables for $characterId ===")
        variables.forEach { variable ->
            val value = getValue(variable)
            Log.d(TAG, "${variable.name} (${variable.type}): $value")
        }
    }
}