package com.example.mascotforge.widget

/**
 * ウィジェット関連の定数を一元管理
 * マジックナンバーを排除し、保守性を向上
 */
object WidgetConstants {

    // === 時間関連 ===
    object Time {
        const val ONE_SECOND_MS = 1_000L
        const val ONE_MINUTE_MS = 60 * ONE_SECOND_MS
        const val ONE_HOUR_MS = 60 * ONE_MINUTE_MS
    }

    // === ウィジェットサイズ閾値（dp） ===
    object WidgetSize {
        const val COMPACT_THRESHOLD_DP = 180    // 2マス以下
        const val LARGE_THRESHOLD_DP = 240      // 4マス以上
    }

    // === フォントサイズ（sp） ===
    object FontSize {
        // 天気アイコン
        const val WEATHER_ICON_COMPACT_SP = 16f
        const val WEATHER_ICON_LARGE_SP = 22f

        // 気温表示
        const val WEATHER_TEMP_COMPACT_SP = 9f
        const val WEATHER_TEMP_LARGE_SP = 11f
    }
}