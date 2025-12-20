package com.mascotforge.character

import org.json.JSONObject

/**
 * カスタム変数の定義
 */
data class CustomVariable(
    val name: String,              // 変数名
    val type: VariableType,        // 型
    val initialValue: Any,         // 初期値
    val min: Int? = null,          // 最小値（number型のみ）
    val max: Int? = null,          // 最大値（number型のみ）
    val changeRules: List<ChangeRule> = emptyList()  // 更新ルール
) {
    enum class VariableType {
        NUMBER,   // 数値
        STRING,   // 文字列
        BOOLEAN   // 真偽値
    }

    /**
     * 変数更新ルール
     */
    data class ChangeRule(
        val trigger: Trigger,       // 発火タイミング
        val condition: String?,     // 条件式（任意）
        val action: Action,         // 実行アクション
        val value: Any?             // 更新値（actionによって使い分け）
    ) {
        enum class Trigger {
            ON_LAUNCH,           // 起動時
            ON_SPEECH,           // セリフ表示時
            ON_CONSECUTIVE_DAYS, // 連続起動日数変化時
            ON_TIME_SLOT_CHANGE  // 時間帯変化時（morning→afternoon等）
        }

        enum class Action {
            SET,        // 値を直接設定
            INCREMENT,  // 値を増やす（number型のみ）
            DECREMENT,  // 値を減らす（number型のみ）
            TOGGLE      // 真偽値を反転（boolean型のみ）
        }
    }
}

/**
 * JSONから CustomVariable をパース
 */
fun parseCustomVariable(name: String, json: JSONObject): CustomVariable {
    val typeStr = json.optString("type", "string")
    val type = when (typeStr.lowercase()) {
        "number" -> CustomVariable.VariableType.NUMBER
        "boolean" -> CustomVariable.VariableType.BOOLEAN
        else -> CustomVariable.VariableType.STRING
    }

    val initialValue: Any = when (type) {
        CustomVariable.VariableType.NUMBER -> json.optInt("initial", 0)
        CustomVariable.VariableType.BOOLEAN -> json.optBoolean("initial", false)
        CustomVariable.VariableType.STRING -> json.optString("initial", "")
    }

    val min = json.optInt("min", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
    val max = json.optInt("max", Int.MAX_VALUE).takeIf { it != Int.MAX_VALUE }

    val changeRules = parseChangeRules(json.optJSONArray("onChange"))

    return CustomVariable(
        name = name,
        type = type,
        initialValue = initialValue,
        min = min,
        max = max,
        changeRules = changeRules
    )
}

/**
 * 変更ルールのパース
 */
private fun parseChangeRules(rulesArray: org.json.JSONArray?): List<CustomVariable.ChangeRule> {
    if (rulesArray == null) return emptyList()

    val rules = mutableListOf<CustomVariable.ChangeRule>()

    for (i in 0 until rulesArray.length()) {
        val ruleJson = rulesArray.getJSONObject(i)

        val triggerStr = ruleJson.optString("trigger", "onLaunch")
        val trigger = when (triggerStr) {
            "onLaunch" -> CustomVariable.ChangeRule.Trigger.ON_LAUNCH
            "onSpeech" -> CustomVariable.ChangeRule.Trigger.ON_SPEECH
            "onConsecutiveDays" -> CustomVariable.ChangeRule.Trigger.ON_CONSECUTIVE_DAYS
            "onTimeSlotChange" -> CustomVariable.ChangeRule.Trigger.ON_TIME_SLOT_CHANGE
            else -> CustomVariable.ChangeRule.Trigger.ON_LAUNCH
        }

        val condition = ruleJson.optString("condition", null)

        val actionStr = ruleJson.optString("action", "set")
        val action = when (actionStr) {
            "set" -> CustomVariable.ChangeRule.Action.SET
            "increment" -> CustomVariable.ChangeRule.Action.INCREMENT
            "decrement" -> CustomVariable.ChangeRule.Action.DECREMENT
            "toggle" -> CustomVariable.ChangeRule.Action.TOGGLE
            else -> CustomVariable.ChangeRule.Action.SET
        }

        val value: Any? = when {
            ruleJson.has("value") -> ruleJson.get("value")
            else -> null
        }

        rules.add(
            CustomVariable.ChangeRule(
                trigger = trigger,
                condition = condition,
                action = action,
                value = value
            )
        )
    }

    return rules
}