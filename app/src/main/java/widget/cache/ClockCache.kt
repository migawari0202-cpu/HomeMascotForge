package com.example.mascotforge.widget.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log

/**
 * デジタル時計描画のキャッシュ管理
 */
object ClockCache {
    private const val TAG = "ClockCache"

    //（RemoteViewsのメモリ制限対策）
    private const val CLOCK_WIDTH = 400   // 240 → 400 (大きくする)
    private const val CLOCK_HEIGHT = 120  // 80 → 120 (大きくする)

    private var cachedBitmap: Bitmap? = null
    private var cachedHour: Int = -1
    private var cachedMinute: Int = -1

    /**
     * デジタル時計Bitmapを取得（時刻が同じならキャッシュ再利用）
     */
    fun getClockBitmap(context: Context, hour: Int, minute: Int): Bitmap {
        Log.d(TAG, "getClockBitmap: $hour:$minute")

        val current = cachedBitmap
        if (current != null && !current.isRecycled
            && cachedHour == hour && cachedMinute == minute
        ) {
            Log.d(TAG, "Cache HIT")
            return current
        }

        Log.d(TAG, "Cache MISS - creating new bitmap")
        val newBitmap = createClockBitmap(hour, minute)

        cachedBitmap = newBitmap
        cachedHour = hour
        cachedMinute = minute

        return newBitmap
    }

    private fun createClockBitmap(hour: Int, minute: Int): Bitmap {
        // ARGB_8888 を使用して透明背景をサポート
        val bitmap = Bitmap.createBitmap(CLOCK_WIDTH, CLOCK_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 背景を透明に設定
        canvas.drawColor(Color.TRANSPARENT)

        // デジタル時刻描画
        drawDigitalTime(canvas, hour, minute)

        Log.d(TAG, "Created bitmap: ${bitmap.width}x${bitmap.height}, bytes=${bitmap.byteCount}")
        return bitmap
    }

    private fun drawDigitalTime(canvas: Canvas, hour: Int, minute: Int) {
        val paint = Paint().apply {
            color = Color.WHITE  // 黒 → 白に戻す
            textSize = 80f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val timeText = String.format("%02d:%02d", hour, minute)

        // 中心に描画
        val x = CLOCK_WIDTH / 2f
        val y = CLOCK_HEIGHT / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(timeText, x, y, paint)
    }

    /**
     * キャッシュクリア
     */
    fun clear() {
        Log.d(TAG, "Cache cleared")
        cachedBitmap?.recycle()  // ★ メモリ解放を追加
        cachedBitmap = null
        cachedHour = -1
        cachedMinute = -1
    }
}