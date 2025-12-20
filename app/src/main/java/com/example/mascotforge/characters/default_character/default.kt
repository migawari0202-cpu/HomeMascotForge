package com.example.mascotforge.characters.default_character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.mascotforge.CharacterProvider
import com.mascotforge.speech.SpeechContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * デフォルトキャラクター（英語ファイル名統一版 + 画像キャッシュ対応）
 *
 * 【フォールバックルール】
 * 1. special_<event>_<timeSlot>.txt (例: special_christmas_morning.txt)
 * 2. <weather>_<tempCat>_<timeSlot>.txt (例: clear_hot_morning.txt)
 * 3. <weather>_<timeSlot>.txt (例: rain_evening.txt)
 * 4. <timeSlot>.txt (例: morning.txt)
 * 5. default.txt
 *
 * 【ファイル名規則】
 * - 全て英語（小文字 + アンダースコア）
 * - 天気: clear, cloudy, rain, thunder, snow, storm, fog, drizzle
 * - 気温: cold, mild, hot
 * - 時間: morning, afternoon, evening, night, midnight
 * - 特殊日: 事前定義マップで変換
 */
class DefaultCharacter(private val context: Context) : CharacterProvider {

    companion object {
        private const val CHAR_FOLDER = "characters/default_character/images"
        private const val SPEECH_FOLDER = "characters/default_character/speeches"

        /**
         * 祝日・特殊日の英語マッピング
         */
        private val SPECIAL_DAY_MAP = mapOf(
            "元日" to "new_year",
            "成人の日" to "coming_of_age",
            "建国記念の日" to "foundation_day",
            "天皇誕生日" to "emperors_birthday",
            "春分の日" to "vernal_equinox",
            "昭和の日" to "showa_day",
            "憲法記念日" to "constitution_day",
            "みどりの日" to "greenery_day",
            "こどもの日" to "childrens_day",
            "海の日" to "marine_day",
            "山の日" to "mountain_day",
            "敬老の日" to "respect_aged",
            "秋分の日" to "autumnal_equinox",
            "スポーツの日" to "sports_day",
            "文化の日" to "culture_day",
            "勤労感謝の日" to "labor_thanksgiving",

            // 世界の記念日
            "クリスマス" to "christmas",
            "クリスマスイブ" to "christmas_eve",
            "バレンタインデー" to "valentine",
            "ホワイトデー" to "white_day",
            "ハロウィン" to "halloween",
            "イースター" to "easter",
            "母の日" to "mothers_day",
            "父の日" to "fathers_day",

            // 日本の季節行事
            "節分" to "setsubun",
            "ひな祭り" to "hinamatsuri",
            "七夕" to "tanabata",
            "お盆" to "obon",
            "十五夜" to "moon_viewing",
            "七五三" to "shichigosan",
            "大晦日" to "new_year_eve"
        )
    }

    override val id: String = "default"
    override val name: String = "デフォルトキャラクター"

    private enum class Emotion(val fileName: String) {
        HAPPY("happy"),
        NORMAL("normal"),
        WORRIED("worried"),
        SAD("sad"),
        SLEEPY("sleepy"),
        EXCITED("excited")
    }

    // 画像キャッシュ: 感情ごとに1回だけロード
    private val imageCache = mutableMapOf<Emotion, Bitmap?>()
    private var defaultImageCache: Bitmap? = null

    /** アセットファイルの存在確認 */
    private fun assetExists(fileName: String): Boolean {
        return try {
            context.assets.open("$SPEECH_FOLDER/$fileName").close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** アセットからセリフを読み込む */
    private fun loadSpeechesFromAssets(fileName: String): List<String> {
        val path = "$SPEECH_FOLDER/$fileName"
        return try {
            context.assets.open(path).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 気温を3段階に分類 */
    private fun temperatureCategory(temp: Int): String = when {
        temp <= 5 -> "cold"
        temp <= 24 -> "mild"
        else -> "hot"
    }

    /** 天気コードをそのまま使用 */
    private fun weatherKeyword(ctx: SpeechContext): String = ctx.weatherCode

    /** 祝日・特殊日の名前を英語に変換 */
    private fun normalizeSpecialDay(name: String): String {
        if (name.matches(Regex("^[a-zA-Z_\\s]+$"))) {
            return name.lowercase().replace("\\s+".toRegex(), "_")
        }
        return SPECIAL_DAY_MAP[name] ?: name.lowercase().replace("\\s+".toRegex(), "_")
    }

    /** セリフファイル名の候補リスト */
    private fun candidateSpeechFileNames(ctx: SpeechContext): List<String> {
        val timeSlot = ctx.timeSlot
        val tempCat = temperatureCategory(ctx.temperature)
        val weather = weatherKeyword(ctx)

        return buildList {
            ctx.specialDayName?.let { name ->
                val normalized = normalizeSpecialDay(name)
                add("special_${normalized}_$timeSlot.txt")
                add("special_${normalized}.txt")
            }

            ctx.holidayName?.let { name ->
                val normalized = normalizeSpecialDay(name)
                add("holiday_${normalized}_$timeSlot.txt")
                add("holiday_${normalized}.txt")
            }

            add("${weather}_${tempCat}_$timeSlot.txt")
            add("${weather}_$timeSlot.txt")
            add("${tempCat}_$timeSlot.txt")
            add("$timeSlot.txt")
            add("default.txt")
        }.distinct()
    }

    /** 現在の状況に合うセリフリストを選択 */
    private fun chooseSpeechLines(ctx: SpeechContext): List<String> {
        val candidates = candidateSpeechFileNames(ctx)
        for (fileName in candidates) {
            if (assetExists(fileName)) {
                val lines = loadSpeechesFromAssets(fileName)
                if (lines.isNotEmpty()) return lines
            }
        }
        return emptyList()
    }

    /** 現在の状況から感情を判断 */
    private fun determineEmotion(ctx: SpeechContext): Emotion {
        return when {
            ctx.isBedtime() && ctx.isNearBedtime -> Emotion.SLEEPY
            ctx.isStormy() -> Emotion.WORRIED
            ctx.isSnowing() -> Emotion.SAD
            ctx.isSunny() && ctx.isWeekend && ctx.isWakeupTime() -> Emotion.HAPPY
            ctx.isSunny() && !ctx.isWeekend && ctx.isDusk() -> Emotion.EXCITED
            ctx.isLowBattery -> Emotion.WORRIED
            else -> Emotion.NORMAL
        }
    }

    override suspend fun getSpeech(context: SpeechContext): String? {
        val lines = chooseSpeechLines(context)
        return lines.randomOrNull()
    }

    /** 感情に応じたキャラ画像を取得（キャッシュ優先） */
    override fun getCharaImage(context: SpeechContext): Bitmap? {
        val emotion = determineEmotion(context)

        // キャッシュに存在すればそれを返す
        if (imageCache.containsKey(emotion)) {
            return imageCache[emotion]
        }

        // キャッシュにない場合は読み込んでキャッシュに保存
        val fileName = "$CHAR_FOLDER/character_${emotion.fileName}.webp"
        val bitmap = try {
            this.context.assets.open(fileName).use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                removeRedBackground(bmp)
            }
        } catch (_: Exception) {
            loadDefaultImage()
        }

        imageCache[emotion] = bitmap
        return bitmap
    }

    /** フォールバック画像をロード（キャッシュ対応） */
    private fun loadDefaultImage(): Bitmap? {
        // デフォルト画像のキャッシュがあれば返す
        defaultImageCache?.let { return it }

        // キャッシュにない場合は読み込んでキャッシュに保存
        val bitmap = try {
            context.assets.open("$CHAR_FOLDER/character.webp").use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                removeRedBackground(bmp)
            }
        } catch (_: Exception) {
            null
        }

        defaultImageCache = bitmap
        return bitmap
    }

    override fun isAvailable(): Boolean = true

    /** 赤背景を透過処理 */
    private fun removeRedBackground(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        original.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)

            pixels[i] = if (red > 180 && green < 100 && blue < 100) Color.TRANSPARENT else pixel
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * キャッシュをクリアする（メモリ解放が必要な場合に呼び出す）
     */
    fun clearCache() {
        imageCache.values.forEach { it?.recycle() }
        imageCache.clear()
        defaultImageCache?.recycle()
        defaultImageCache = null
    }
}