package com.example.mascotforge.characters.evil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider

/**
 * セキュリティテストキャラクターのFactory
 */
class EvilCharacterFactory : CharacterFactory {

    override fun getCharacterId(): String = "evil"

    override fun create(context: Context): CharacterProvider {
        return EvilCharacter(context)
    }

    override fun getThumbnail(context: Context): Drawable? {
        return BitmapDrawable(context.resources, createThumbnailBitmap())
    }

    override fun getDisplayName(context: Context): String {
        return "🔐 セキュリティテスター"
    }

    override fun getDescription(context: Context): String {
        return "アプリのセキュリティ状態をチェックするキャラクター。危険な権限の使用状況を報告します。"
    }

    override fun getAuthor(context: Context): String {
        return "開発チーム"
    }

    override fun getVersion(): String {
        return "2.0.0"
    }

    /**
     * サムネイル用の小さいセキュリティバッジを生成
     */
    private fun createThumbnailBitmap(): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 背景（濃い青）
        canvas.drawColor(Color.parseColor("#1a237e"))

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // 盾の形
        paint.color = Color.parseColor("#00e676")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(64f, 56f, 32f, paint)
        canvas.drawRect(32f, 56f, 96f, 96f, paint)

        // チェックマーク
        paint.color = Color.WHITE
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(48f, 72f, 58f, 82f, paint)
        canvas.drawLine(58f, 82f, 80f, 52f, paint)

        return bitmap
    }
}