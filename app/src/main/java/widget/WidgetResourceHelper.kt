package com.example.mascotforge.widget

import android.content.Context
import android.widget.RemoteViews
import com.example.mascotforge.R
import widget.WidgetConstants

    /**
     * 動的に drawable を名前で取得
     * - 今後アイコン増えてもコード修正不要
     */
    private fun getDrawableByName(name: String): Int? {
        return try {
            val field = R.drawable::class.java.getField(name)
            field.getInt(null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * メモTextViewを動的に取得
     * - memo_text_1_normal
     * - memo_text_2_normal
     * - memo_text_3_normal（増えても勝手に対応）
     */
    fun getMemoTextId(index: Int): Int? {
        val name = "memo_text_${index + 1}_normal"
        return getIdByName(name)
    }

    private fun getIdByName(name: String): Int? {
        return try {
            val field = R.id::class.java.getField(name)
            field.getInt(null)
        } catch (_: Exception) {
            null
        }
    }

    fun getClockId(): Int {
        return R.id.widget_clock_normal
    }

    fun getWeatherFontSizes(isCompact: Boolean): Pair<Float, Float> {
        return if (isCompact) {
            Pair(
                WidgetConstants.FontSize.WEATHER_ICON_COMPACT_SP,
                WidgetConstants.FontSize.WEATHER_TEMP_COMPACT_SP
            )
        } else {
            Pair(
                WidgetConstants.FontSize.WEATHER_ICON_LARGE_SP,
                WidgetConstants.FontSize.WEATHER_TEMP_LARGE_SP
            )
        }
    }

/* ========= RemoteViews safe extensions ========= */

fun RemoteViews.setTextViewTextSafely(viewId: Int, text: String?) {
    text?.let { this.setTextViewText(viewId, it) }
}

fun RemoteViews.setImageResourceSafely(viewId: Int, resId: Int?) {
    resId?.let { this.setImageViewResource(viewId, it) }
}

/* ========= Widget size mode ========= */

enum class WidgetSizeMode {
    TINY,
    COMPACT,
    LARGE;

    companion object {
        fun fromWidth(widthDp: Int): WidgetSizeMode {
            return when {
                widthDp < WidgetConstants.WidgetSize.COMPACT_THRESHOLD_DP -> TINY
                widthDp < WidgetConstants.WidgetSize.LARGE_THRESHOLD_DP -> COMPACT
                else -> LARGE
            }
        }
    }

    val isCompact: Boolean get() = this == COMPACT
    val showMemos: Boolean get() = this != TINY
}
