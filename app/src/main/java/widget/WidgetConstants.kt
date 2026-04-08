package widget

/**
 * ウィジェット関連の定数を一元管理
 * マジックナンバーを排除し、保守性を向上
 */
object WidgetConstants {

    // === 時間関連 ===
    object Time {
        const val ONE_SECOND_MS = 1_000L
        const val ONE_MINUTE_MS = 60 * ONE_SECOND_MS
        const val FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS
        const val TWENTY_MINUTES_MS = 20 * ONE_MINUTE_MS
        const val THIRTY_SECONDS_MS = 30 * ONE_SECOND_MS
        const val ONE_HOUR_MS = 60 * ONE_MINUTE_MS
        const val SIX_HOURS_MS = 6 * ONE_HOUR_MS
    }

    // === キャッシュ有効期限 ===
    object CacheDuration {
        const val BATTERY_MS = 2 * Time.ONE_MINUTE_MS       // 2分
        const val WEATHER_MS = Time.ONE_HOUR_MS             // 1時間
        const val MEMO_MS = 3 * Time.ONE_SECOND_MS          // 3秒
        const val SPEECH_MS = 5 * Time.ONE_MINUTE_MS        // 5分
    }

    // === 更新間隔 ===
    object UpdateInterval {
        const val CLOCK_MS = Time.ONE_MINUTE_MS             // 1分
        const val WEATHER_MS = Time.ONE_HOUR_MS             // 1時間
        const val SPEECH_MS = Time.TWENTY_MINUTES_MS        // 20分
        const val BATTERY_MS = Time.FIVE_MINUTES_MS         // 5分
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

    // === バッテリーレベル閾値 ===
    object BatteryLevel {
        const val LEVEL_FULL = 80
        const val LEVEL_HIGH = 60
        const val LEVEL_MEDIUM = 40
        const val LEVEL_LOW = 20
    }

    object PrefsKeys {
        // ファイル名
        const val WEATHER_PREFS = "weather_prefs"
        const val MEMO_PREFS = "widget_memos"

        // キー名
        const val KEY_WEATHER_EMOJI = "lastWeatherEmoji"
        const val KEY_WEATHER_TEMP = "lastTemp"
        const val KEY_WEATHER_CODE = "lastWeatherCode"       // Int型のweatherId
        const val KEY_WEATHER_DESC = "lastDescription"       // ★ 追加: String型の識別子
        const val KEY_MEMOS = "widget_memos"
    }
    // === 制限値 ===
    object Limits {
        const val MAX_MEMOS = 1000
        const val MAX_VISIBLE_MEMOS = 2
    }

    // === リクエストコード（PendingIntent用） ===
    object RequestCode {
        const val MEMO_BUTTON = 0
        const val GAME_BUTTON = 1000
        const val ALARM_CLOCK = 1001
        const val ALARM_WEATHER = 1002
        const val ALARM_SPEECH = 1003
    }

    // === ログタグ ===
    object LogTag {
        const val WIDGET_PROVIDER = "TimeWidgetProvider"
        const val CLOCK_CACHE = "ClockCache"
        const val BATTERY_MANAGER = "BatteryManager"
        const val MEMO_CACHE = "MemoCache"
        const val WEATHER_CACHE = "WeatherCache"
        const val SCHEDULER = "WidgetUpdateScheduler"
    }
}