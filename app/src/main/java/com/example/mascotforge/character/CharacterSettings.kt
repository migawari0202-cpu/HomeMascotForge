package com.example.mascotforge.character

import android.content.Context
import android.graphics.Color
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * キャラクター個別設定（任意）
 *
 * - character.json と同階層に Settings.json を置く
 * - ない場合は「設定なし」として扱う（例: 画像の切り抜きは無効）
 */
data class CharacterSettings(
    val imageCutout: ImageCutoutSettings? = null
) {
    data class ImageCutoutSettings(
        val defaultTolerance: Int = 30,
        val byTag: Map<String, CutoutSpec> = emptyMap()
    )

    data class CutoutSpec(
        val colors: List<Int>,
        val tolerance: Int? = null
    )

    fun findCutoutSpec(tag: String?): CutoutSpec? {
        if (imageCutout == null) return null
        if (tag == null) return imageCutout.byTag["*"]
        return imageCutout.byTag[tag] ?: imageCutout.byTag["*"]
    }
}

object CharacterSettingsLoader {
    private const val TAG = "CharacterSettings"
    private const val SETTINGS_FILE_NAME = "Settings.json"
    private const val MAX_SETTINGS_BYTES = 256 * 1024 // 256KB (任意設定なので小さめに制限)
    private const val MAX_COLORS_PER_TAG = 10

    /**
     * ConcurrentHashMap は null キー/値を禁止する。
     * Settings.json が無い・無効な場合は「未設定」をこのセンチネルでキャッシュし、
     * 呼び出し側には null を返す。
     */
    private val NO_SETTINGS = CharacterSettings()

    // key: source.basePath → 実設定 or NO_SETTINGS（未設定）
    private val cache = ConcurrentHashMap<String, CharacterSettings>()

    fun load(context: Context, source: CharacterSource, forceReload: Boolean = false): CharacterSettings? {
        val key = source.basePath
        if (key.isEmpty()) {
            Log.w(TAG, "Empty basePath; Settings.json load skipped")
            return null
        }
        if (!forceReload) {
            val cached = cache[key]
            if (cached != null) {
                return if (cached === NO_SETTINGS) null else cached
            }
        }

        val settings = try {
            val text = readSettingsText(context, source) ?: run {
                cache[key] = NO_SETTINGS
                return null
            }
            if (text.toByteArray(Charsets.UTF_8).size > MAX_SETTINGS_BYTES) {
                Log.w(TAG, "Settings.json too large, ignored: $key")
                cache[key] = NO_SETTINGS
                return null
            }
            parse(JSONObject(text))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Settings.json: $key", e)
            cache[key] = NO_SETTINGS
            return null
        }

        cache[key] = settings
        return settings
    }

    private fun readSettingsText(context: Context, source: CharacterSource): String? {
        return when (source) {
            is CharacterSource.Assets -> {
                val path = "${source.basePath}/$SETTINGS_FILE_NAME"
                try {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    null
                }
            }
            is CharacterSource.InstalledFiles -> {
                val file = File(context.filesDir, "${source.basePath}/$SETTINGS_FILE_NAME")
                if (!file.isFile) return null
                try {
                    file.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    internal fun parse(json: JSONObject): CharacterSettings {
        val imageCutoutJson = json.optJSONObject("imageCutout")
        val imageCutout = if (imageCutoutJson != null) parseImageCutout(imageCutoutJson) else null
        return CharacterSettings(imageCutout = imageCutout)
    }

    private fun parseImageCutout(json: JSONObject): CharacterSettings.ImageCutoutSettings {
        val defaultTolerance = json.optInt("defaultTolerance", 30).coerceIn(0, 255)
        val byTag = mutableMapOf<String, CharacterSettings.CutoutSpec>()

        val byTagJson = json.optJSONObject("byTag")
        if (byTagJson != null) {
            byTagJson.keys().forEach { tag ->
                val v = byTagJson.opt(tag) ?: return@forEach
                val spec = parseCutoutSpec(v, defaultTolerance) ?: return@forEach
                byTag[tag] = spec
            }
        }

        return CharacterSettings.ImageCutoutSettings(
            defaultTolerance = defaultTolerance,
            byTag = byTag
        )
    }

    private fun parseCutoutSpec(value: Any, defaultTolerance: Int): CharacterSettings.CutoutSpec? {
        return when (value) {
            is JSONArray -> {
                val colors = parseColors(value)
                if (colors.isEmpty()) null else CharacterSettings.CutoutSpec(colors = colors, tolerance = null)
            }
            is JSONObject -> {
                val tol = value.optInt("tolerance", defaultTolerance).coerceIn(0, 255)
                val colorsVal = value.opt("colors")
                val colors = when (colorsVal) {
                    is JSONArray -> parseColors(colorsVal)
                    is String -> parseColors(JSONArray().put(colorsVal))
                    else -> emptyList()
                }
                if (colors.isEmpty()) null else CharacterSettings.CutoutSpec(colors = colors, tolerance = tol)
            }
            is String -> {
                val colors = parseColors(JSONArray().put(value))
                if (colors.isEmpty()) null else CharacterSettings.CutoutSpec(colors = colors, tolerance = null)
            }
            else -> null
        }
    }

    private fun parseColors(arr: JSONArray): List<Int> {
        val colors = ArrayList<Int>(minOf(arr.length(), MAX_COLORS_PER_TAG))
        for (i in 0 until arr.length()) {
            if (colors.size >= MAX_COLORS_PER_TAG) break
            val s = arr.optString(i, "").trim()
            if (s.isEmpty()) continue
            parseColorIntOrNull(s)?.let { colors += it }
        }
        return colors
    }

    private fun parseColorIntOrNull(raw: String): Int? {
        // "#RRGGBB" / "#AARRGGBB" / "0xAARRGGBB" / "0xRRGGBB" / decimal
        val s = raw.trim()
        return try {
            when {
                s.startsWith("#") -> Color.parseColor(s)
                s.startsWith("0x", ignoreCase = true) -> {
                    val v = s.removePrefix("0x").removePrefix("0X")
                    val n = v.toLong(16)
                    when (v.length) {
                        6 -> (0xFF000000L or n).toInt()
                        8 -> n.toInt()
                        else -> null
                    }
                }
                else -> s.toLongOrNull()?.toInt()
            }
        } catch (_: Exception) {
            null
        }
    }
}
