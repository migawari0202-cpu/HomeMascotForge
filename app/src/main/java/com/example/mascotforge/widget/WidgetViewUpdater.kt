package com.example.mascotforge.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import com.example.mascotforge.R
import com.example.mascotforge.widget.cache.UserWeatherCache
import java.util.Locale

/**
 * WidgetViewUpdater - レイアウトタイプ対応版（修正版）
 * COMPACT モードのView IDを正しく設定
 */
class WidgetViewUpdater(private val context: Context) {

    companion object {
        private const val TAG = "WidgetViewUpdater"
    }

    enum class LayoutType {
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
            LayoutType.COMPACT -> R.id.widget_battery_icon_compact
            else -> null
        }

        fun batteryPercent(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_battery_percent_normal
            LayoutType.COMPACT -> R.id.widget_battery_percent_compact
            else -> null
        }

        fun weatherIcon(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.weather_icon_normal
            LayoutType.COMPACT -> R.id.weather_icon_compact
            else -> null
        }

        fun weatherTemp(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.weather_temp_normal
            LayoutType.COMPACT -> R.id.weather_temp_compact
            else -> null
        }

        fun speech(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_speech_normal
            LayoutType.COMPACT -> R.id.widget_speech_compact
        }

        fun characterImage(layoutType: LayoutType): Int? = when (layoutType) {
            LayoutType.NORMAL -> R.id.widget_character_image
            LayoutType.COMPACT -> R.id.widget_character_image_compact
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

            val cached = UserWeatherCache(context).getCurrentWeather()
            val emoji = cached?.weatherEmoji ?: "--"
            val tempStr = if (cached != null) "${formatTemp(cached.temperature)}°C" else "--"

            views.safeSetText(iconViewId, emoji)
            views.safeSetTextSizeSp(iconViewId, if (layoutType == LayoutType.COMPACT) 23f else 23f)
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
                    LayoutType.COMPACT -> 11f
                    LayoutType.NORMAL -> 12f
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
