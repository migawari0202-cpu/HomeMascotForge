package widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import com.example.mascotforge.R
import java.util.*
import widget.cache.ClockCache

/**
 * WidgetViewUpdater - レイアウトタイプ対応版（修正版）
 * ✅ COMPACT モードのView IDを正しく設定
 */
class WidgetViewUpdater(private val context: Context) {

    companion object {
        private const val TAG = "WidgetViewUpdater"
        private const val WEATHER_PREFS = "user_weather_prefs"
        private const val KEY_LAST_WEATHER_EMOJI = "current_weather_emoji"
        private const val KEY_LAST_TEMP = "current_temp"
        private const val KEY_LAST_WEATHER_CODE = "current_weather_code"
        private const val KEY_LAST_DESCRIPTION = "lastDescription"
        private const val KEY_LAST_CITY_NAME = "lastCityName"
    }

    enum class LayoutType {
        MINI,
        COMPACT,
        NORMAL
    }

    /**
     * View IDをレイアウトタイプに応じて取得
     * ✅ COMPACTモードのIDを正しく設定
     */
    private object ViewIds {

        fun batteryIcon(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_battery_icon_normal
            LayoutType.COMPACT -> R.id.widget_battery_icon_compact  // 🔧 修正
            else -> null
        }

        fun batteryPercent(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_battery_percent_normal
            LayoutType.COMPACT -> R.id.widget_battery_percent_compact  // 🔧 修正
            else -> null
        }

        fun weatherIcon(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.weather_icon_normal
            LayoutType.COMPACT -> R.id.weather_icon_compact  // 🔧 修正
            else -> null
        }

        fun weatherTemp(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.weather_temp_normal
            LayoutType.COMPACT -> R.id.weather_temp_compact  // 🔧 修正
            else -> null
        }

        fun speech(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_speech_normal
            LayoutType.COMPACT -> R.id.widget_speech_compact  // 🔧 修正
            else -> R.id.widget_speech_normal
        }

        fun characterImage(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_character_image
            LayoutType.COMPACT -> R.id.widget_character_image_compact  // 🔧 修正
            else -> R.id.widget_character_image
        }

        fun clock(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_clock_normal
            LayoutType.COMPACT -> R.id.widget_clock_compact  // 🔧 修正
            else -> null
        }
    }


    /**
     * 時計表示を更新（NORMAL・COMPACT対応）
     */
    fun updateClockViews(
        views: RemoteViews,
        clockCache: Any?,
        widgetId: Int,
        hour: Int,
        minute: Int,
        minWidth: Int
    ) {
        try {
            // NORMAL または COMPACT のいずれかで時計を更新
            val clockViewId = ViewIds.clock(LayoutType.NORMAL)
                ?: ViewIds.clock(LayoutType.COMPACT)
                ?: return

            val bitmap: Bitmap? = if (clockCache != null && clockCache is ClockCache) {
                ClockCache.getClockBitmap(context, hour, minute)
            } else {
                null
            }

            if (bitmap != null && !bitmap.isRecycled) {
                views.safeSetImageBitmap(clockViewId, bitmap)
                Log.v(TAG, "Clock: ${bitmap.width}x${bitmap.height}")
            } else {
                Log.w(TAG, "Clock bitmap null, using fallback")
                val timeText = String.format(Locale.JAPAN, "%02d:%02d", hour, minute)
                val fallbackBitmap = createTextBitmap(timeText, 200, 200, 48f)
                views.safeSetImageBitmap(clockViewId, fallbackBitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateClockViews failed", e)
        }
    }

    /**
     * バッテリー表示を更新（レイアウトタイプ対応）
     */
    fun updateBatteryViews(
        views: RemoteViews,
        level: Int,
        isCharging: Boolean,
        layoutType: LayoutType
    ) {
        try {
            val iconViewId = ViewIds.batteryIcon(layoutType) ?: return
            val percentViewId = ViewIds.batteryPercent(layoutType) ?: return

            val iconRes = getBatteryIconResource(level, isCharging)
            views.safeSetImageResource(iconViewId, iconRes)
            views.safeSetText(percentViewId, "$level%")

            Log.d(TAG, "Battery updated for $layoutType: $level%, charging=$isCharging")
        } catch (e: Exception) {
            Log.e(TAG, "updateBatteryViews failed for $layoutType", e)
        }
    }

    /**
     * 天気表示を更新（レイアウトタイプ対応）
     */
    fun updateWeatherViews(
        views: RemoteViews,
        weather: Any?,
        minWidth: Int,
        layoutType: LayoutType
    ) {
        try {
            val iconViewId = ViewIds.weatherIcon(layoutType) ?: return
            val tempViewId = ViewIds.weatherTemp(layoutType) ?: return

            val prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val emojiFromPrefs = prefs.getString(KEY_LAST_WEATHER_EMOJI, null)
            val tempFromPrefs = prefs.getFloat(KEY_LAST_TEMP, Float.NaN)

            val emojiFromObject = tryExtractEmojiFromObject(weather)
            val tempFromObject = tryExtractTempFromObject(weather)

            val emoji = emojiFromPrefs ?: emojiFromObject ?: "☀️"
            val tempStr = if (!tempFromPrefs.isNaN()) {
                "${formatTemp(tempFromPrefs)}°C"
            } else {
                tempFromObject ?: "25°C"
            }

            views.safeSetText(iconViewId, emoji)
            views.safeSetTextSizeSp(iconViewId, if (layoutType == LayoutType.COMPACT) 23f else 23f)  // 🔧 COMPACTも20spに
            views.safeSetText(tempViewId, tempStr)
            views.safeSetTextSizeSp(tempViewId, if (layoutType == LayoutType.COMPACT) 10f else 14f)

            Log.d(TAG, "Weather updated for $layoutType: $emoji $tempStr")
        } catch (e: Exception) {
            Log.e(TAG, "updateWeatherViews failed for $layoutType", e)
        }
    }

    /**
     * メモ表示を更新（通常版のみ）
     */
    fun updateMemoViews(views: RemoteViews, memoTexts: List<String>, textSize: Float, layoutId: Int) {
        if (layoutId != R.layout.widget_normal) return

        val memoViewIds = listOf(
            R.id.memo_text_1_normal,
            R.id.memo_text_2_normal
        )

        memoViewIds.forEachIndexed { index, viewId ->
            if (index < memoTexts.size && memoTexts[index].isNotEmpty()) {
                val text = if (memoTexts[index].length > 15) {
                    memoTexts[index].substring(0, 15) + "…"
                } else {
                    memoTexts[index]
                }

                views.setViewVisibility(viewId, android.view.View.VISIBLE)
                views.safeSetText(viewId, "- $text")
                views.safeSetTextSizeSp(viewId, textSize)
            } else {
                views.setTextViewText(viewId, "")
                views.setViewVisibility(viewId, android.view.View.GONE)
            }
        }
    }

    /**
     * セリフ表示を更新（レイアウトタイプ対応）
     */
    fun updateSpeechViews(views: RemoteViews, speech: String, layoutType: LayoutType) {
        try {
            val speechViewId = ViewIds.speech(layoutType) ?: return

            if (speech.isNotEmpty() && speech != "...") {
                views.setViewVisibility(speechViewId, android.view.View.VISIBLE)
                views.safeSetText(speechViewId, speech)

                val textSize = when (layoutType) {
                    LayoutType.COMPACT -> 11f  // 🔧 XMLの設定に合わせる
                    LayoutType.NORMAL -> 12f
                    else -> 14f
                }
                views.safeSetTextSizeSp(speechViewId, textSize)

                Log.d(TAG, "Speech updated for $layoutType: $speech")
            } else {
                views.setViewVisibility(speechViewId, android.view.View.GONE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateSpeechViews failed for $layoutType", e)
        }
    }

    /**
     * キャラクター画像を更新
     */
    fun updateCharacterImageViews(
        views: RemoteViews,
        characterBitmap: Bitmap?,
        layoutType: LayoutType
    ) {
        try {
            val imageId = ViewIds.characterImage(layoutType) ?: return

            if (characterBitmap != null && !characterBitmap.isRecycled) {
                views.setViewVisibility(imageId, android.view.View.VISIBLE)
                views.safeSetImageBitmap(imageId, characterBitmap)
                Log.d(TAG, "Character image set for $layoutType: ${characterBitmap.width}x${characterBitmap.height}")
            } else {
                views.setViewVisibility(imageId, android.view.View.GONE)
                Log.w(TAG, "Character bitmap null or recycled for $layoutType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateCharacterImageViews failed for $layoutType", e)
        }
    }

    // -------------------------
    // ヘルパー関数
    // -------------------------

    private fun createTextBitmap(text: String, width: Int, height: Int, textSize: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            this.textSize = textSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val xPos = width / 2f
        val yPos = (height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, paint)
        return bitmap
    }

    private fun RemoteViews.safeSetText(viewId: Int, text: CharSequence?) {
        try { setTextViewText(viewId, text ?: "") } catch (e: Exception) {
            Log.w(TAG, "Failed to set text for view $viewId", e)
        }
    }

    private fun RemoteViews.safeSetTextSizeSp(viewId: Int, sizeSp: Float) {
        try {
            setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set text size for view $viewId", e)
        }
    }

    private fun RemoteViews.safeSetImageBitmap(viewId: Int, bitmap: Bitmap?) {
        try {
            if (bitmap != null) {
                setImageViewBitmap(viewId, bitmap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set image bitmap for view $viewId", e)
        }
    }

    private fun RemoteViews.safeSetImageResource(viewId: Int, resId: Int) {
        try {
            setImageViewResource(viewId, resId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set image resource for view $viewId", e)
        }
    }

    private fun tryExtractEmojiFromObject(weather: Any?): String? {
        if (weather == null) return null
        return try {
            val method = weather::class.java.getMethod("getEmoji")
            method.invoke(weather) as? String
        } catch (_: Exception) {
            try {
                val field = weather::class.java.getDeclaredField("emoji")
                field.isAccessible = true
                field.get(weather) as? String
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun tryExtractTempFromObject(weather: Any?): String? {
        if (weather == null) return null
        return try {
            try {
                val method = weather::class.java.getMethod("getTemperature")
                "${method.invoke(weather)}°C"
            } catch (_: NoSuchMethodException) {
                val field = weather::class.java.getDeclaredField("temperature")
                field.isAccessible = true
                "${field.get(weather)}°C"
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTemp(temp: Float): String {
        return String.format(Locale.JAPAN, "%.1f", temp)
    }

    private fun getBatteryIconResource(level: Int, isCharging: Boolean): Int {

        // フルのときだけ共通アイコン
        if (level >= 80) return R.drawable.b5

        val prefix = if (isCharging) "bc" else "b"

        val suffix = when {
            level >= 60 -> 4
            level >= 40 -> 3
            level >= 20 -> 2
            else -> 1
        }

        return getDrawableId("${prefix}$suffix")
    }

    private fun getDrawableId(name: String): Int {
        return try {
            val field = R.drawable::class.java.getField(name)
            field.getInt(null)
        } catch (_: Exception) {
            R.drawable.b1 // fallback
        }
    }

}