package widget.cache

import android.content.Context
import android.util.Log

/**
 * UserWeatherCache（位置情報非保存版）
 * - 位置情報を保持しない安全設計
 * - 天気状態のみキャッシュ
 */
class UserWeatherCache(private val context: Context) {

    companion object {
        private const val TAG = "UserWeatherCache"
        private const val CACHE_PREFS = "user_weather_prefs"

        private const val KEY_CURRENT_EMOJI = "current_weather_emoji"
        private const val KEY_CURRENT_TEMP = "current_temp"
        private const val KEY_CURRENT_CODE = "current_weather_code"
        private const val KEY_CURRENT_TIMESTAMP = "current_timestamp"

        private const val KEY_PREV_EMOJI = "prev_weather_emoji"
        private const val KEY_PREV_TEMP = "prev_temp"
        private const val KEY_PREV_CODE = "prev_weather_code"
        private const val KEY_PREV_TIMESTAMP = "prev_timestamp"

        private const val STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000L // 2時間
    }

    private val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    /** 現在の天気を取得（有効期限付き） */
    fun getCurrentWeather(): WeatherInfo? {
        val emoji = prefs.getString(KEY_CURRENT_EMOJI, null) ?: return null
        val temp = prefs.getFloat(KEY_CURRENT_TEMP, Float.NaN)
        val code = prefs.getString(KEY_CURRENT_CODE, null) ?: return null
        val timestamp = prefs.getLong(KEY_CURRENT_TIMESTAMP, 0L)

        if (System.currentTimeMillis() - timestamp > STALE_THRESHOLD_MS) {
            Log.d(TAG, "Current weather is stale")
            return null
        }

        return if (!temp.isNaN()) WeatherInfo(temp, emoji, code) else null
    }

    /** 前回の天気を取得 */
    fun getPreviousWeather(): WeatherInfo? {
        val emoji = prefs.getString(KEY_PREV_EMOJI, null) ?: return null
        val temp = prefs.getFloat(KEY_PREV_TEMP, Float.NaN)
        val code = prefs.getString(KEY_PREV_CODE, null) ?: return null
        return if (!temp.isNaN()) WeatherInfo(temp, emoji, code) else null
    }

    /** Worker から天気を同期（位置情報を保存しない） */
    @Synchronized
    fun updateFromWorker(newWeather: WeatherInfo) {
        // 期限チェックなしで現在の値を取得
        val currentTimestamp = prefs.getLong(KEY_CURRENT_TIMESTAMP, 0L)
        val current = if (currentTimestamp > 0) {
            val emoji = prefs.getString(KEY_CURRENT_EMOJI, null)
            val temp = prefs.getFloat(KEY_CURRENT_TEMP, Float.NaN)
            val code = prefs.getString(KEY_CURRENT_CODE, null)
            if (emoji != null && code != null && !temp.isNaN()) {
                WeatherInfo(temp, emoji, code)
            } else null
        } else null

        val now = System.currentTimeMillis()
        val editor = prefs.edit()

        // 前回データがあれば保存（期限切れでも）
        if (current != null) {
            editor.putString(KEY_PREV_EMOJI, current.weatherEmoji)
            editor.putFloat(KEY_PREV_TEMP, current.temperature)
            editor.putString(KEY_PREV_CODE, current.weatherCode)
            editor.putLong(KEY_PREV_TIMESTAMP, currentTimestamp) // 元のタイムスタンプ保持
            Log.d(TAG, "前回の天気を保存: ${current.weatherCode} (${currentTimestamp})")
        }

        editor.putString(KEY_CURRENT_EMOJI, newWeather.weatherEmoji)
        editor.putFloat(KEY_CURRENT_TEMP, newWeather.temperature)
        editor.putString(KEY_CURRENT_CODE, newWeather.weatherCode)
        editor.putLong(KEY_CURRENT_TIMESTAMP, now)
        editor.commit() // 同期保存

        Log.d(TAG, "天気キャッシュ更新: ${newWeather.weatherCode} ${newWeather.temperature}℃ at $now")
    }

    /** 天気が変わったかどうか */
    fun hasWeatherChanged(): Boolean {
        val current = getCurrentWeather()
        val previous = getPreviousWeather()
        if (current == null || previous == null) return false
        val changed = current.weatherCode != previous.weatherCode
        if (changed) Log.d(TAG, "Weather changed: ${previous.weatherCode} → ${current.weatherCode}")
        return changed
    }

    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Weather cache cleared")
    }
}

/** 天気データ構造（位置情報なし） */
data class WeatherInfo(
    val temperature: Float,
    val weatherEmoji: String,
    val weatherCode: String
)
