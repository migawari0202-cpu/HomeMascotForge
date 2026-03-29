package com.example.mascotforge.characters.default_character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider

class DefaultCharacterFactory : CharacterFactory {

    override fun getCharacterId(): String = "default"

    override fun create(context: Context): CharacterProvider = DefaultCharacter(context)

    override fun getThumbnail(context: Context): Drawable? = try {
        context.assets.open("characters/default_character/images/thumbnail.webp").use { input ->
            val raw = BitmapFactory.decodeStream(input) ?: return null
            BitmapDrawable(context.resources, removeRedBackground(raw))
        }
    } catch (_: Exception) { null }

    private fun removeRedBackground(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            Color.RGBToHSV(Color.red(pixels[i]), Color.green(pixels[i]), Color.blue(pixels[i]), hsv)
            if ((hsv[0] <= 20f || hsv[0] >= 340f) && hsv[1] >= 0.35f && hsv[2] >= 0.15f) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        source.recycle()
        return out
    }

    override fun getDisplayName(context: Context): String = "みなと"

    override fun getDescription(context: Context): String =
        "天気・時間・季節に応じてセリフが変化するデフォルトキャラクター。"

    override fun getAuthor(context: Context): String = "開発チーム"

    override fun getVersion(): String = "2.0.0"
}
