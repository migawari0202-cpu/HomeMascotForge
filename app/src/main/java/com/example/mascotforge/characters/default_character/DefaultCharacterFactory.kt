package com.example.mascotforge.characters.default_character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.character.CharacterSource
import com.example.mascotforge.character.SafeCharacterLoader

class DefaultCharacterFactory : CharacterFactory {

    companion object {
        private const val TAG = "DefaultCharacterFactory"
    }

    override fun getCharacterId(): String = "default"

    override fun create(context: Context): CharacterProvider {
        // DynamicCharacter を使用して petting.txt を含むセリフ選択に対応
        return try {
            val loader = SafeCharacterLoader(context)
            val source = CharacterSource.Assets("characters/default_character")
            val character = loader.loadCharacter(source)
            if (character != null) {
                Log.d(TAG, "Loaded default character as DynamicCharacter")
                character
            } else {
                Log.w(TAG, "Failed to load default character as DynamicCharacter, fallback to DefaultCharacter")
                DefaultCharacter(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception loading default character, fallback to DefaultCharacter: ${e.message}")
            DefaultCharacter(context)
        }
    }

    override fun getThumbnail(context: Context): Drawable? = try {
        context.assets.open("characters/default_character/images/character.png").use { input ->
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

        // 整数演算のみを使用（浮動小数点精度の問題を排除）
        val targetColor = 0xFFfc0000.toInt()
        val tr = (targetColor shr 16) and 0xFF
        val tg = (targetColor shr 8)  and 0xFF
        val tb =  targetColor         and 0xFF
        val toleranceSq = 30 * 30  // tolerance = 30

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val distSq = (r - tr) * (r - tr) +
                         (g - tg) * (g - tg) +
                         (b - tb) * (b - tb)

            if (distSq < toleranceSq) {
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
