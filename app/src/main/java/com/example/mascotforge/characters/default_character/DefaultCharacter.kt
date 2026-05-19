package com.example.mascotforge.characters.default_character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.character.speech.TagParser
import com.example.mascotforge.character.CharacterBitmapCache
import com.example.mascotforge.speech.SpeechContext
import java.io.BufferedReader
import java.io.InputStreamReader

class DefaultCharacter(private val context: Context) : CharacterProvider {

    companion object {
        private const val CHAR_FOLDER = "characters/default_character/images"
        private const val SPEECH_FOLDER = "characters/default_character/speeches"

        private val WEATHER_KEY_MAP = mapOf(
            "晴れ" to "clear",
            "曇り" to "cloudy",
            "雨" to "rain",
            "小雨" to "rain",
            "雷雨" to "thunder",
            "雪" to "snow",
            "嵐" to "storm",
            "台風" to "storm",
            "霧" to "fog",
            "霧雨" to "drizzle"
        )

        private val SPECIAL_DAY_MAP = mapOf(
            "元日" to "new_year",
            "クリスマス" to "christmas",
            "クリスマスイブ" to "christmas_eve"
        )
    }

    override val id: String = "default"
    override val name: String = "みなと"

    private enum class Emotion(val fileName: String) {
        NORMAL("character"),
        HAPPY("happy"),
        EXCITED("excited"),
        SAD("sad"),
        ANGRY("angry"),
        WORRIED("worried"),
        SHY("shy"),
        SLEEPY("sleepy")
    }

    private val shownSpeeches = mutableSetOf<String>()

    @Volatile private var extractedEmotionKey: String? = null

    // ===================== セリフ =====================

    override suspend fun getSpeech(context: SpeechContext): String? {
        val allLines = chooseSpeechLines(context)
        if (allLines.isEmpty()) return null

        val unshown = allLines.filter { it !in shownSpeeches }
        val raw = if (unshown.size > 1) unshown.random()
        else {
            shownSpeeches.clear()
            allLines.random()
        }

        shownSpeeches += raw
        val parsed = TagParser().parse(raw)
        extractedEmotionKey = parsed.emotion
        return expandVariables(parsed.cleanedText, context)
    }

    private fun expandVariables(text: String, ctx: SpeechContext): String {
        var result = text
        val variables = mapOf(
            "userName" to ctx.userName,
            "hour" to ctx.hour.toString(),
            "minute" to ctx.minute.toString()
        )
        variables.forEach { (k, v) -> result = result.replace("{$k}", v) }
        return result
    }

    private fun chooseSpeechLines(ctx: SpeechContext): List<String> {
        val pool = mutableListOf<String>()
        pool.addAll(loadSpeeches("default.txt"))

        for (fileName in relevantFileNames(ctx)) {
            if (assetExists(fileName)) {
                pool.addAll(loadSpeeches(fileName))
            }
        }
        return pool.distinct()
    }

    private fun relevantFileNames(ctx: SpeechContext): List<String> {
        val slot = ctx.timeSlot
        val weather = WEATHER_KEY_MAP.entries
            .firstOrNull { ctx.weatherCode.contains(it.key) }
            ?.value ?: "clear"

        return listOf(
            "$slot.txt",
            "$weather.txt",
            "${weather}_$slot.txt"
        )
    }

    private fun assetExists(fileName: String): Boolean = try {
        context.assets.open("$SPEECH_FOLDER/$fileName").close()
        true
    } catch (_: Exception) { false }

    private fun loadSpeeches(fileName: String): List<String> = try {
        context.assets.open("$SPEECH_FOLDER/$fileName").use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
    } catch (_: Exception) { emptyList() }

    // ===================== 画像 =====================

    override fun getCharaImage(context: SpeechContext): Bitmap? {
        val tagEmotion = extractedEmotionKey
            ?.let { key -> Emotion.entries.find { it.fileName == key } }
        extractedEmotionKey = null

        val emotion = tagEmotion ?: Emotion.NORMAL
        val cacheKey = "default:${emotion.fileName}"
        
        // グローバルキャッシュで一貫性を保証
        CharacterBitmapCache.get(cacheKey)?.let { return it }
        
        val loaded = loadImage(emotion)
        if (loaded != null) {
            CharacterBitmapCache.put(cacheKey, loaded)
        }
        return loaded
    }

    private fun loadImage(emotion: Emotion): Bitmap? = try {
        context.assets.open("$CHAR_FOLDER/${emotion.fileName}.webp").use { stream ->
            val src = BitmapFactory.decodeStream(stream) ?: return null
            removeRedBackground(src, targetColor = 0xFFfc0000.toInt(), tolerance = 30)
        }
    } catch (_: Exception) { null }

    private fun removeRedBackground(src: Bitmap, targetColor: Int, tolerance: Int): Bitmap {
        val tr = (targetColor shr 16) and 0xFF
        val tg = (targetColor shr 8)  and 0xFF
        val tb =  targetColor         and 0xFF

        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        // 整数演算のみを使用（浮動小数点精度の問題を排除）
        val toleranceSq = tolerance * tolerance

        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8)  and 0xFF
            val b =  pixels[i]         and 0xFF

            val distSq = (r - tr) * (r - tr) +
                         (g - tg) * (g - tg) +
                         (b - tb) * (b - tb)

            if (distSq < toleranceSq) pixels[i] = 0x00000000
        }

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        src.recycle()
        return result
    }

    override fun isAvailable(): Boolean = true

    fun clearCache() {
        // グローバルキャッシュは外部で管理
    }
}