package com.example.mascotforge.characters.default_character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.mascotforge.CharacterProvider
import com.mascotforge.character.speech.TagParser
import com.mascotforge.speech.SpeechContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * デフォルトキャラクター（みなと）
 *
 * 【セリフファイル選択の優先順位】
 * 1. special_<event>_<timeSlot>.txt  (例: special_christmas_morning.txt)
 * 2. holiday_<name>_<timeSlot>.txt   (例: holiday_new_year_morning.txt)
 * 3. <weather>_<tempCat>_<timeSlot>.txt (例: rain_cold_morning.txt)
 * 4. <weather>_<timeSlot>.txt        (例: rain_morning.txt)
 * 5. <timeSlot>.txt                  (例: morning.txt)
 * 6. default.txt
 *
 * 【感情判定の優先順位】
 * バッテリー危機→おこ / 低下・嵐→心配 / 深夜・早朝→眠い
 * 月曜朝・日曜夜・雨雪→悲しい / 記念日・正月・クリスマス→うれしい
 * 金曜夜・祝日→わくわく / おかえり→てれ / それ以外→普通
 */
class DefaultCharacter(private val context: Context) : CharacterProvider {

    companion object {
        private const val CHAR_FOLDER = "characters/default_character/images"
        private const val SPEECH_FOLDER = "characters/default_character/speeches"

        /** 日本語天気コード → ファイル名キー */
        private val WEATHER_KEY_MAP = mapOf(
            "晴れ"   to "clear",
            "曇り"   to "cloudy",
            "雨"     to "rain",
            "小雨"   to "rain",
            "雷雨"   to "thunder",
            "雪"     to "snow",
            "嵐"     to "storm",
            "台風"   to "storm",
            "霧"     to "fog",
            "霧雨"   to "drizzle"
        )

        /** 祝日・特殊日の英語マッピング */
        private val SPECIAL_DAY_MAP = mapOf(
            "元日"       to "new_year",
            "成人の日"   to "coming_of_age",
            "春分の日"   to "vernal_equinox",
            "昭和の日"   to "showa_day",
            "憲法記念日" to "constitution_day",
            "みどりの日" to "greenery_day",
            "こどもの日" to "childrens_day",
            "海の日"     to "marine_day",
            "山の日"     to "mountain_day",
            "敬老の日"   to "respect_aged",
            "秋分の日"   to "autumnal_equinox",
            "文化の日"   to "culture_day",
            "勤労感謝の日" to "labor_thanksgiving",
            "天皇誕生日" to "emperors_birthday",
            "クリスマス"     to "christmas",
            "クリスマスイブ" to "christmas_eve",
            "バレンタインデー" to "valentine",
            "ホワイトデー"   to "white_day",
            "ハロウィン"     to "halloween",
            "母の日"     to "mothers_day",
            "父の日"     to "fathers_day",
            "節分"       to "setsubun",
            "ひな祭り"   to "hinamatsuri",
            "七夕"       to "tanabata",
            "お盆"       to "obon",
            "十五夜"     to "moon_viewing",
            "七五三"     to "shichigosan",
            "大晦日"     to "new_year_eve"
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

    private val imageCache = mutableMapOf<Emotion, Bitmap?>()
    private val shownSpeeches = mutableSetOf<String>()

    @Volatile private var extractedEmotionKey: String? = null

    // ===================== セリフ =====================

    override suspend fun getSpeech(context: SpeechContext): String? {
        val allLines = chooseSpeechLines(context)
        if (allLines.isEmpty()) return null

        val unshown = allLines.filter { it !in shownSpeeches }
        val raw = if (unshown.size > 1) {
            unshown.random()
        } else {
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
            "userName"           to ctx.userName,
            "hour"               to ctx.hour.toString(),
            "minute"             to ctx.minute.toString(),
            "month"              to ctx.month.toString(),
            "day"                to ctx.day.toString(),
            "dayOfWeek"          to ctx.dayOfWeek,
            "timeSlot"           to ctx.timeSlot,
            "weatherCode"        to ctx.weatherCode,
            "weatherEmoji"       to ctx.weatherEmoji,
            "temperature"        to ctx.temperature.toString(),
            "temperatureFeeling" to ctx.temperatureFeeling,
            "batteryLevel"       to ctx.batteryLevel.toString(),
            "season"             to ctx.season,
            "specialDayName"     to (ctx.specialDayName ?: ""),
            "holidayName"        to (ctx.holidayName ?: ""),
            "consecutiveDays"    to ctx.consecutiveDays.toString(),
        )
        variables.forEach { (key, value) -> result = result.replace("{$key}", value) }
        return result
    }

    private fun chooseSpeechLines(ctx: SpeechContext): List<String> {
        val pool = mutableListOf<String>()

        // default は常にベースとして含める
        pool.addAll(loadSpeeches("default.txt"))

        // 現在の状況に関係するファイルを全部追加（存在するものだけ）
        for (fileName in relevantFileNames(ctx)) {
            if (assetExists(fileName)) {
                pool.addAll(loadSpeeches(fileName))
            }
        }

        return pool.distinct()
    }

    /**
     * 現在の状況に「関係する」ファイル名一覧
     * default.txt は除外（chooseSpeechLines で別途追加）
     *
     * 「部分一致でも出す」方針：
     * - 時間帯だけ一致するファイルも含める
     * - 天気だけ一致するファイルも含める
     * - より具体的な組み合わせも含める
     * → 全部マージしてプールを作るので重複は distinct() で除去
     */
    private fun relevantFileNames(ctx: SpeechContext): List<String> {
        val slot = ctx.timeSlot
        val tempCat = temperatureCategory(ctx.temperature)
        val weather = WEATHER_KEY_MAP.entries
            .firstOrNull { ctx.weatherCode.contains(it.key) }
            ?.value ?: "clear"

        return buildList {
            // 時間帯
            add("$slot.txt")

            // 天気（部分一致）
            add("$weather.txt")
            add("${weather}_$slot.txt")
            add("${weather}_${tempCat}_$slot.txt")

            // 特殊日
            ctx.specialDayName?.let { name ->
                val key = toFileKey(name)
                add("special_$key.txt")
                add("special_${key}_$slot.txt")
            }

            // 祝日
            ctx.holidayName?.let { name ->
                val key = toFileKey(name)
                add("holiday_$key.txt")
                add("holiday_${key}_$slot.txt")
            }
        }.distinct()
    }

    private fun toFileKey(name: String): String =
        SPECIAL_DAY_MAP[name] ?: name.lowercase().replace(Regex("\\s+"), "_")

    private fun temperatureCategory(temp: Int): String = when {
        temp <= 5  -> "cold"
        temp <= 24 -> "mild"
        else       -> "hot"
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

        val emotion = tagEmotion ?: determineEmotion(context)
        return imageCache.getOrPut(emotion) { loadImage(emotion) }
    }

    private fun determineEmotion(ctx: SpeechContext): Emotion = when {
        // バッテリー危機 → おこ
        ctx.isCriticalBattery()                          -> Emotion.ANGRY
        // バッテリー低下・嵐・熱中症リスク → 心配
        ctx.shouldWarnBattery()                          -> Emotion.WORRIED
        ctx.isStormy()                                   -> Emotion.WORRIED
        ctx.isHeatStrokeRisk()                           -> Emotion.WORRIED
        // 深夜・就寝時間・早朝 → 眠い
        ctx.isLateNight() || ctx.isNearBedtime           -> Emotion.SLEEPY
        ctx.isEarlyMorning()                             -> Emotion.SLEEPY
        // 月曜朝・日曜夜・雨・雪 → 悲しい
        ctx.isMondayMorning()                            -> Emotion.SAD
        ctx.isSundayNight()                              -> Emotion.SAD
        ctx.isRaining() || ctx.isSnowing()               -> Emotion.SAD
        // 連続記念日・お正月・クリスマス・初回起動 → うれしい
        ctx.isMilestoneContinuation()                    -> Emotion.HAPPY
        ctx.isNewYear() || ctx.isChristmas()             -> Emotion.HAPPY
        ctx.isFirstLaunchToday && ctx.isFirstToday()     -> Emotion.HAPPY
        // 金曜夜・祝日・快晴の週末朝 → わくわく
        ctx.isFridayNight()                              -> Emotion.EXCITED
        ctx.isHoliday || ctx.isSpecialDay                -> Emotion.EXCITED
        ctx.isSunny() && ctx.isWeekend && ctx.isWakeupTime() -> Emotion.EXCITED
        // おかえり → てれ
        ctx.isWelcomeBack()                              -> Emotion.SHY
        // それ以外 → 普通
        else                                             -> Emotion.NORMAL
    }

    private fun loadImage(emotion: Emotion): Bitmap? = try {
        context.assets.open("$CHAR_FOLDER/${emotion.fileName}.webp").use {
            val raw = BitmapFactory.decodeStream(it) ?: return null
            removeRedBackground(raw)
        }
    } catch (_: Exception) { null }

    /**
     * 赤背景クロマキー除去（HSV方式）
     * - 色相が赤の範囲（0-20°, 340-360°）かつ彩度が高いピクセルを透明化
     * - エッジの半赤ピクセルは彩度に応じて段階的に透明化
     */
    private fun removeRedBackground(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]   // 0-360
            val sat = hsv[1]   // 0-1
            val value = hsv[2] // 0-1

            // 赤の色相範囲（0-20° または 340-360°）かつ彩度・明度が一定以上
            val inRedHue = hue <= 20f || hue >= 340f
            if (!inRedHue || sat < 0.35f || value < 0.15f) continue

            pixels[i] = Color.TRANSPARENT
        }

        out.setPixels(pixels, 0, w, 0, 0, w, h)
        source.recycle()
        return out
    }

    override fun isAvailable(): Boolean = true

    fun clearCache() {
        imageCache.values.forEach { it?.recycle() }
        imageCache.clear()
    }
}
