package com.example.mascotforge.speech

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import java.time.LocalDateTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.mascotforge.character.CharacterStateManager
import com.example.mascotforge.widget.cache.UserWeatherCache

/**
 * SpeechContextを生成するヘルパー
 *
 * 【データソース】
 * - 時刻・日付: システム時計
 * - 天気: UserWeatherCache (WeatherUpdateWorker から更新)
 * - バッテリー: BatteryManager
 * - アプリ使用状況: SharedPreferences
 * - キャラクター状態: CharacterStateManager
 */
object SpeechContextFactory {

    private const val TAG = "SpeechContextFactory"
    private const val PREFS_NAME = "mascot_usage"
    private const val KEY_LAUNCH_COUNT = "launch_count_"
    private const val KEY_LAST_LAUNCH = "last_launch"
    private const val KEY_CONSECUTIVE_DAYS = "consecutive_days"
    private const val KEY_LAST_LAUNCH_DATE = "last_launch_date"

    private const val KEY_USER_NAME = "user_name"

    /**
     * 現在の状況からSpeechContextを作成
     *
     * @param context Android Context
     * @param characterId キャラクターID（状態管理・タッチ追跡に使用。デフォルト: "default"）
     * @return 現在の状況を反映したSpeechContext
     */
    fun create(
        context: Context,
        characterId: String = "default"
    ): SpeechContext {
        val now = LocalDateTime.now()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userName = prefs.getString(KEY_USER_NAME, "ユーザー") ?: "ユーザー"

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 時刻系
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val hour = now.hour
        val minute = now.minute
        val timeSlot = getTimeSlot(hour)

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 日付系
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val month = now.monthValue
        val day = now.dayOfMonth
        val dayOfWeek = getDayOfWeekString(now.dayOfWeek)
        val isWeekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY

        // 祝日判定（簡易版）
        val holidayInfo = getHolidayInfo(month, day)
        val isHoliday = holidayInfo != null
        val holidayName = holidayInfo

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 季節・特殊日
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val season = getSeason(month, day)
        val specialDayInfo = getSpecialDay(month, day)
        val isSpecialDay = specialDayInfo != null
        val specialDayName = specialDayInfo

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 天気系（UserWeatherCache から取得）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val weatherCache = UserWeatherCache(context)
        val cachedWeather = weatherCache.getCurrentWeather()

        val weatherEmoji: String
        val weatherCode: String
        val temperature: Int

        if (cachedWeather != null) {
            // キャッシュがある場合（2時間以内に更新された天気）
            val rawEmoji = cachedWeather.weatherEmoji
            weatherEmoji = if (rawEmoji == "☀️" && hour >= 19) "🌙" else rawEmoji
            weatherCode = convertWeatherCodeToJapanese(cachedWeather.weatherCode)
            temperature = cachedWeather.temperature.toInt()
            Log.d(TAG, "天気をキャッシュから取得: $weatherEmoji $weatherCode ${temperature}℃")
        } else {
            // キャッシュがない場合（初回起動 or 天気取得失敗）
            weatherEmoji = "?"
            weatherCode = "不明"
            temperature = 20
            Log.d(TAG, "天気キャッシュなし、デフォルト値を使用")
        }

        val temperatureFeeling = getTemperatureFeeling(temperature)
        val humidity: Int? = null  // 現状APIから湿度を取得していないため

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // デバイス状態
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val batteryInfo = getBatteryInfo(context)
        val batteryLevel = batteryInfo.level
        val isCharging = batteryInfo.isCharging
        val isLowBattery = batteryLevel <= 20
        val batteryStatus = when {
            isCharging -> "充電中"
            batteryLevel <= 15 -> "省電力モード"
            else -> "通常"
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // アプリ使用状況
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val today = now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)  // "2026-03-20" 形式
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT + today, 0) + 1
        val lastLaunchMillis = prefs.getLong(KEY_LAST_LAUNCH, 0L)
        val lastLaunchHoursAgo = if (lastLaunchMillis > 0) {
            ((System.currentTimeMillis() - lastLaunchMillis) / (1000 * 60 * 60)).toInt()
        } else null

        val lastLaunchDate = prefs.getString(KEY_LAST_LAUNCH_DATE, "")
        val isFirstLaunchToday = lastLaunchDate != today

        // 連続起動日数を計算
        val consecutiveDays = calculateConsecutiveDays(prefs, today)

        // 使用状況を保存
        prefs.edit().apply {
            putInt(KEY_LAUNCH_COUNT + today, launchCount)
            putLong(KEY_LAST_LAUNCH, System.currentTimeMillis())
            putString(KEY_LAST_LAUNCH_DATE, today)
            putInt(KEY_CONSECUTIVE_DAYS, consecutiveDays)
            apply()
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CharacterState & タッチ関連情報
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val stateManager = CharacterStateManager(context)
        val charState = stateManager.getState(characterId)

        val touchCount = charState.touchCount
        val touchCountToday = charState.touchCountToday
        val lastTouchMinutesAgo = (charState.getTimeSinceLastTouch() / 60).toInt().coerceAtMost(Int.MAX_VALUE)
        val wasTouched = charState.getTimeSinceLastTouch() < 60 // 1分以内にタッチされたか
        val isBeingPetted = charState.getTimeSinceLastTouch() <= 10 &&
                charState.consecutiveTouchCount >= 2
        val consecutiveTouchCount = if (charState.getTimeSinceLastTouch() <= 10) {
            charState.consecutiveTouchCount
        } else {
            0
        }
        val pettingLevel = when {
            !isBeingPetted -> 0
            consecutiveTouchCount >= 8 -> 3
            consecutiveTouchCount >= 4 -> 2
            else -> 1
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // その他コンテキスト
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 就寝時刻を22:00と仮定
        val isNearBedtime = hour >= 21 || hour <= 1
        // 起床時刻を7:00と仮定
        val isNearWakeup = hour in 6..8
        // 月齢（簡易版、実装は省略）
        val moonPhase: String? = null  // TODO: 月齢計算APIを使用

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SpeechContext を生成して返す
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        return SpeechContext(
            timeSlot = timeSlot,
            hour = hour,
            minute = minute,
            month = month,
            day = day,
            dayOfWeek = dayOfWeek,
            isWeekend = isWeekend,
            isHoliday = isHoliday,
            holidayName = holidayName,
            season = season,
            isSpecialDay = isSpecialDay,
            specialDayName = specialDayName,
            weatherEmoji = weatherEmoji,
            weatherCode = weatherCode,
            temperature = temperature,
            temperatureFeeling = temperatureFeeling,
            humidity = humidity,
            batteryLevel = batteryLevel,
            batteryStatus = batteryStatus,
            isCharging = isCharging,
            isLowBattery = isLowBattery,
            launchCount = launchCount,
            lastLaunchHoursAgo = lastLaunchHoursAgo,
            isFirstLaunchToday = isFirstLaunchToday,
            consecutiveDays = consecutiveDays,
            userName = userName,
            userGender = null,
            isNearBedtime = isNearBedtime,
            isNearWakeup = isNearWakeup,
            moonPhase = moonPhase,
            lastTouchMinutesAgo = lastTouchMinutesAgo,
            touchCountToday = touchCountToday,
            wasTouched = wasTouched,
            touchCount = touchCount,
            consecutiveTouchCount = consecutiveTouchCount,
            pettingLevel = pettingLevel,
            isBeingPetted = isBeingPetted,
            characterState = charState,
        )
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // プライベートヘルパー関数
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 時間帯を判定
     */
    private fun getTimeSlot(hour: Int): String = when(hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        in 21..23 -> "night"
        else -> "midnight"
    }

    /**
     * 曜日を文字列に変換
     */
    private fun getDayOfWeekString(dayOfWeek: DayOfWeek): String = when(dayOfWeek) {
        DayOfWeek.MONDAY -> "月曜日"
        DayOfWeek.TUESDAY -> "火曜日"
        DayOfWeek.WEDNESDAY -> "水曜日"
        DayOfWeek.THURSDAY -> "木曜日"
        DayOfWeek.FRIDAY -> "金曜日"
        DayOfWeek.SATURDAY -> "土曜日"
        DayOfWeek.SUNDAY -> "日曜日"
    }

    /**
     * 季節を判定
     */
    private fun getSeason(month: Int, day: Int): String = when {
        month in 3..5 -> "春"
        month == 6 && day <= 15 -> "春"
        month == 6 && day > 15 -> "梅雨"
        month == 7 && day <= 15 -> "梅雨"
        month == 7 && day > 15 -> "夏"
        month == 8 -> "夏"
        month in 9..11 -> "秋"
        else -> "冬"
    }

    /**
     * 気温の体感を判定
     */
    private fun getTemperatureFeeling(temperature: Int): String = when {
        temperature >= 30 -> "暑い"
        temperature >= 25 -> "少し暑い"
        temperature >= 15 -> "ちょうどいい"
        temperature >= 10 -> "少し寒い"
        else -> "寒い"
    }

    /**
     * 天気コードを日本語に変換
     *
     * WeatherUpdateWorker が英語コード ("clear", "rain" など) を保存しているため、
     * キャラ製作者が日本語で条件を書けるように変換する。
     *
     * @param code 英語の天気コード
     * @return 日本語の天気表現
     */
    private fun convertWeatherCodeToJapanese(code: String): String = when (code) {
        "clear" -> "晴れ"
        "partly_cloudy" -> "晴れ時々曇り"
        "cloudy" -> "曇り"
        "rain" -> "雨"
        "drizzle" -> "小雨"
        "thunder" -> "雷雨"
        "snow" -> "雪"
        "fog" -> "霧"
        "storm" -> "嵐"
        else -> {
            Log.w(TAG, "Unknown weather code: $code, defaulting to '晴れ'")
            "晴れ"  // デフォルト
        }
    }

    /**
     * 日本の祝日を判定（簡易版）
     *
     * 注意: 春分の日・秋分の日は年によって変わるため、簡易的に固定日としています。
     * 正確な判定が必要な場合は、祝日ライブラリの使用を推奨します。
     */
    private fun getHolidayInfo(month: Int, day: Int): String? = when {
        month == 1 && day == 1 -> "元日"
        month == 1 && day in 8..14 && day % 7 == 1 -> "成人の日" // 第2月曜（簡易）
        month == 2 && day == 11 -> "建国記念の日"
        month == 2 && day == 23 -> "天皇誕生日"
        month == 3 && day == 20 -> "春分の日" // 簡易版（年によって変わる）
        month == 4 && day == 29 -> "昭和の日"
        month == 5 && day == 3 -> "憲法記念日"
        month == 5 && day == 4 -> "みどりの日"
        month == 5 && day == 5 -> "こどもの日"
        month == 7 && day in 15..21 && day % 7 == 1 -> "海の日" // 第3月曜（簡易）
        month == 8 && day == 11 -> "山の日"
        month == 9 && day in 15..21 && day % 7 == 1 -> "敬老の日" // 第3月曜（簡易）
        month == 9 && day == 23 -> "秋分の日" // 簡易版（年によって変わる）
        month == 10 && day in 8..14 && day % 7 == 1 -> "スポーツの日" // 第2月曜（簡易）
        month == 11 && day == 3 -> "文化の日"
        month == 11 && day == 23 -> "勤労感謝の日"
        else -> null
    }

    /**
     * 特別な日を判定
     */
    private fun getSpecialDay(month: Int, day: Int): String? = when {
        month == 2 && day == 3 -> "節分"
        month == 2 && day == 14 -> "バレンタインデー"
        month == 3 && day == 14 -> "ホワイトデー"
        month == 7 && day == 7 -> "七夕"
        month == 10 && day == 31 -> "ハロウィン"
        month == 12 && day == 24 -> "クリスマスイブ"
        month == 12 && day == 25 -> "クリスマス"
        month == 12 && day == 31 -> "大晦日"
        else -> null
    }

    /**
     * バッテリー情報を取得
     */
    private fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            100  // 取得失敗時はデフォルト100%
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryInfo(batteryPct, isCharging)
    }

    /**
     * 連続起動日数を計算
     *
     * 昨日も起動していた場合は +1、そうでなければリセットして 1
     */
    private fun calculateConsecutiveDays(
        prefs: android.content.SharedPreferences,
        today: String
    ): Int {
        val lastDateString = prefs.getString(KEY_LAST_LAUNCH_DATE, "") ?: ""
        val currentConsecutive = prefs.getInt(KEY_CONSECUTIVE_DAYS, 0)

        // 初回起動
        if (lastDateString.isEmpty()) {
            return 1
        }

        // 今日既に起動済み
        if (lastDateString == today) {
            return currentConsecutive
        }

        return try {
            // ISO 8601形式 ("YYYY-MM-DD") をパース
            val lastDate = LocalDate.parse(lastDateString, DateTimeFormatter.ISO_LOCAL_DATE)
            val todayDate = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE)

            // 昨日の日付と一致するかをチェック
            val yesterday = todayDate.minusDays(1)
            val isConsecutive = lastDate.isEqual(yesterday)

            // 連続起動であれば +1、そうでなければリセットして 1
            if (isConsecutive) {
                currentConsecutive + 1
            } else {
                1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate consecutive days", e)
            1  // パース失敗時はリセット
        }
    }

    /**
     * バッテリー情報を保持するデータクラス
     */
    private data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean
    )
}
