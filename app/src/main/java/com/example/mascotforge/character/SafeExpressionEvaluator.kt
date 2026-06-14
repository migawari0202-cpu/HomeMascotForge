package com.example.mascotforge.character

import android.util.Log
import com.example.mascotforge.speech.SpeechContext

/**
 * 安全な式評価器（カスタム変数対応版）
 *
 * SpeechContextとカスタム変数を使って条件式を評価する
 */
class SafeExpressionEvaluator(
    private val ctx: SpeechContext,
    private val customVariables: Map<String, String> = emptyMap()
) {

    companion object {
        private const val TAG = "SafeExpressionEvaluator"
        private const val MAX_DEPTH = 5

        /** 天気コードの英語→日本語マッピング（感情ルールの式評価用） */
        private val WEATHER_CODE_EN_TO_JP = mapOf(
            "clear" to "晴れ",
            "partly_cloudy" to "晴れ時々曇り",
            "cloudy" to "曇り",
            "rain" to "雨",
            "drizzle" to "小雨",
            "thunder" to "雷雨",
            "snow" to "雪",
            "fog" to "霧",
            "storm" to "嵐"
        )
    }

    /**
     * 条件式を評価
     *
     * サポートする演算子:
     * - 論理演算: &&, ||, !
     * - 比較演算: ==, !=, <, >, <=, >=
     * - 括弧: ( )
     *
     * 例:
     * - "hour >= 6 && hour < 12"
     * - "isWeekend && !isCharging"
     * - "favorability > 50 || trust > 70"
     */
    fun evaluate(expression: String): Boolean {
        return try {
            evaluateExpression(expression.replace(" ", ""), 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to evaluate expression: $expression", e)
            false
        }
    }

    private fun evaluateExpression(expr: String, depth: Int): Boolean {
        if (depth > MAX_DEPTH) {
            Log.w(TAG, "Expression depth limit exceeded ($MAX_DEPTH): $expr")
            return false
        }

        // OR演算子（最低優先度 = 最初に分割）
        splitTopLevel(expr, "||")?.let { parts ->
            return parts.any { evaluateExpression(it, depth + 1) }
        }

        // AND演算子
        splitTopLevel(expr, "&&")?.let { parts ->
            return parts.all { evaluateExpression(it, depth + 1) }
        }

        // 括弧（外側のみ除去、内側に対応する閉じ括弧があることを確認）
        if (expr.startsWith("(") && expr.endsWith(")") && isMatchingParens(expr)) {
            return evaluateExpression(expr.substring(1, expr.length - 1), depth + 1)
        }

        // 単一条件を評価
        return evaluateSingleCondition(expr)
    }

    /**
     * 括弧の深さを考慮しながらトップレベルの演算子で分割する。
     * 演算子が括弧の中にある場合は無視する。
     * 分割が1つ以下なら null を返す（演算子なし）。
     */
    private fun splitTopLevel(expr: String, operator: String): List<String>? {
        val parts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        var i = 0
        while (i < expr.length) {
            when {
                expr[i] == '(' -> { depth++; current.append(expr[i]) }
                expr[i] == ')' -> { depth--; current.append(expr[i]) }
                depth == 0 && expr.startsWith(operator, i) -> {
                    parts.add(current.toString())
                    current = StringBuilder()
                    i += operator.length
                    continue
                }
                else -> current.append(expr[i])
            }
            i++
        }
        parts.add(current.toString())
        return if (parts.size > 1) parts else null
    }

    /**
     * 文字列の最初と最後の括弧が対応しているか確認する。
     * 例: "(a&&b)" → true, "(a)||(b)" → false（外側括弧は一対でない）
     */
    private fun isMatchingParens(expr: String): Boolean {
        var depth = 0
        for (i in expr.indices) {
            when (expr[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    // 最初の '(' が閉じられたのに文字列が終わっていない
                    if (depth == 0 && i < expr.length - 1) return false
                }
            }
        }
        return depth == 0
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

        // NOT演算子
        if (expr.startsWith("!")) {
            val field = expr.substring(1)
            return getContextValue(field) != "true"
        }

        // 単純な真偽値チェック
        return getContextValue(expr) == "true"
    }

    private fun compareValues(left: String, right: String, op: String): Boolean {
        val leftVal = getContextValue(left)

        // 右辺が文字列リテラル（"..."）の場合
        val rightVal = if (right.startsWith("\"") && right.endsWith("\"")) {
            right.removeSurrounding("\"")
        } else {
            getContextValue(right).ifEmpty { right }
        }

        // 数値比較
        val leftNum = leftVal.toIntOrNull()
        val rightNum = rightVal.toIntOrNull()

        if (leftNum != null && rightNum != null) {
            return when (op) {
                "==" -> leftNum == rightNum
                "!=" -> leftNum != rightNum
                "<" -> leftNum < rightNum
                ">" -> leftNum > rightNum
                "<=" -> leftNum <= rightNum
                ">=" -> leftNum >= rightNum
                else -> false
            }
        }

        // 文字列比較
        val equality = when (op) {
            "==" -> leftVal == rightVal || (left == "weatherCode" && WEATHER_CODE_EN_TO_JP[rightVal] == leftVal)
            "!=" -> {
                val match = leftVal == rightVal || (left == "weatherCode" && WEATHER_CODE_EN_TO_JP[rightVal] == leftVal)
                !match
            }
            else -> false
        }

        return equality
    }

    /**
     * フィールド名から値を取得
     *
     * 優先順位:
     * 1. カスタム変数
     * 2. SpeechContextの標準フィールド
     */
    private fun getContextValue(field: String): String {
        // カスタム変数を優先
        if (customVariables.containsKey(field)) {
            return customVariables[field] ?: ""
        }

        // 標準フィールド
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
            "wasTouched" -> ctx.wasTouched.toString()
            "touchCount" -> ctx.touchCount.toString()
            "touchCountToday" -> ctx.touchCountToday.toString()
            "lastTouchMinutesAgo" -> ctx.lastTouchMinutesAgo.toString()
            "consecutiveTouchCount" -> ctx.consecutiveTouchCount.toString()
            "pettingLevel" -> ctx.pettingLevel.toString()
            "isBeingPetted" -> ctx.isBeingPetted.toString()
            "userName" -> ctx.userName
            "userGender" -> ctx.userGender ?: ""
            "isNearBedtime" -> ctx.isNearBedtime.toString()
            "isNearWakeup" -> ctx.isNearWakeup.toString()
            "moonPhase" -> ctx.moonPhase ?: ""
            "timeSlot" -> ctx.timeSlot
            else -> {
                Log.w(TAG, "Unknown field: $field")
                ""
            }
        }
    }
}
