package com.example.mascotforge.characters.evil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.mascotforge.CharacterProvider
import com.mascotforge.speech.SpeechContext

/**
 * 安全テスト用キャラクター
 */
class EvilCharacter(private val context: Context) : CharacterProvider {

    override val id: String = "evil"
    override val name: String = "セキュリティテスター"

    override suspend fun getSpeech(speechContext: SpeechContext): String? {
        return "⚠️ これは安全テスト用のキャラクターです。権限を要求していません。"
    }

    /**
     * CharacterProvider 定義に合わせて SpeechContext を受け取る
     */
    override fun getCharaImage(speechContext: SpeechContext): Bitmap? {
        val size = 500
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        // 盾
        paint.color = Color.parseColor("#00e676")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(250f, 220f, 120f, paint)
        canvas.drawRect(130f, 220f, 370f, 400f, paint)

        // チェックマーク
        paint.color = Color.WHITE
        paint.strokeWidth = 30f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(180f, 280f, 230f, 330f, paint)
        canvas.drawLine(230f, 330f, 320f, 200f, paint)

        // テキスト
        paint.style = Paint.Style.FILL
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("SECURE", 250f, 460f, paint)

        return bitmap
    }

    override fun isAvailable(): Boolean = true
}
