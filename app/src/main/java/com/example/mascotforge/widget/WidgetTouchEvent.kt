package com.example.mascotforge.widget

internal enum class WidgetTouchTarget { CHARACTER, WEATHER }

internal data class WidgetTouchEvent(val target: WidgetTouchTarget, val timestamp: Long)

/** 発生順を最優先。同時刻のみキャラパックのセリフ優先度で比較し、同点はキャラを選ぶ。 */
internal fun selectWidgetTouchEvent(
    events: List<WidgetTouchEvent>,
    characterPriority: Int,
    weatherPriority: Int
): WidgetTouchEvent? = events.minWithOrNull(
    compareBy<WidgetTouchEvent> { it.timestamp }
        .thenBy { if (it.target == WidgetTouchTarget.CHARACTER) -characterPriority.toLong() else -weatherPriority.toLong() }
        .thenBy { if (it.target == WidgetTouchTarget.CHARACTER) 0 else 1 }
)