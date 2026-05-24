# default キャラ JSON 化 変更差分メモ

このメモは、今回こちらで変更したコード差分をそのまま追えるようにまとめたものです。
要点よりもまず diff を見やすく置くことを優先しています。

## 対象ファイル

- `app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacterFactory.kt`
- `app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacter.kt`
- `app/src/main/java/com/example/mascotforge/CharacterPreferences.kt`
- `app/src/main/java/widget/WidgetCharacterConfig.kt`

## 1. `DefaultCharacterFactory.kt`

### 差分

```diff
diff --git a/app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacterFactory.kt b/app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacterFactory.kt
index 701a8bf..4202e53 100644
--- a/app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacterFactory.kt
+++ b/app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacterFactory.kt
@@ -1,14 +1,13 @@
 package com.example.mascotforge.characters.default_character
 
 import android.content.Context
-import android.graphics.Bitmap
 import android.graphics.BitmapFactory
-import android.graphics.Color
 import android.graphics.drawable.BitmapDrawable
 import android.graphics.drawable.Drawable
 import android.util.Log
 import com.example.mascotforge.CharacterFactory
 import com.example.mascotforge.CharacterProvider
+import com.example.mascotforge.character.CharacterMetadata
 import com.example.mascotforge.character.CharacterSource
 import com.example.mascotforge.character.SafeCharacterLoader
 
@@ -16,75 +15,58 @@ class DefaultCharacterFactory : CharacterFactory {
 
     companion object {
         private const val TAG = "DefaultCharacterFactory"
+        private const val DEFAULT_CHARACTER_ID = "default"
+        private const val DEFAULT_SOURCE_PATH = "characters/default_character"
+        private const val FALLBACK_DISPLAY_NAME = "みなと"
+        private const val FALLBACK_VERSION = "1.0.0"
     }
 
-    override fun getCharacterId(): String = "default"
+    @Volatile
+    private var cachedMetadata: CharacterMetadata? = null
+
+    override fun getCharacterId(): String = DEFAULT_CHARACTER_ID
 
     override fun create(context: Context): CharacterProvider {
-        // DynamicCharacter を使用して petting.txt を含むセリフ選択に対応
-        return try {
-            val loader = SafeCharacterLoader(context)
-            val source = CharacterSource.Assets("characters/default_character")
-            val character = loader.loadCharacter(source)
-            if (character != null) {
-                Log.d(TAG, "Loaded default character as DynamicCharacter")
-                character
-            } else {
-                Log.w(TAG, "Failed to load default character as DynamicCharacter, fallback to DefaultCharacter")
-                DefaultCharacter(context)
-            }
-        } catch (e: Exception) {
-            Log.w(TAG, "Exception loading default character, fallback to DefaultCharacter: ${e.message}")
-            DefaultCharacter(context)
+        val loader = SafeCharacterLoader(context)
+        return requireNotNull(loader.loadCharacter(source())) {
+            "Failed to load default character from asset JSON"
+        }.also {
+            Log.d(TAG, "Loaded default character from asset JSON")
         }
     }
 
     override fun getThumbnail(context: Context): Drawable? = try {
-        context.assets.open("characters/default_character/images/character.png").use { input ->
-            val raw = BitmapFactory.decodeStream(input) ?: return null
-            BitmapDrawable(context.resources, removeRedBackground(raw))
+        val metadata = loadMetadata(context)
+        val imageName = metadata?.imageMapping?.get("character")
+            ?: metadata?.imageMapping?.get("normal")
+            ?: metadata?.imageMapping?.values?.firstOrNull()
+            ?: "character.webp"
+
+        context.assets.open("$DEFAULT_SOURCE_PATH/images/$imageName").use { input ->
+            BitmapFactory.decodeStream(input)?.let { BitmapDrawable(context.resources, it) }
         }
-    } catch (_: Exception) { null }
+    } catch (e: Exception) {
+        Log.w(TAG, "Failed to load thumbnail for default character", e)
+        null
+    }
 
-    private fun removeRedBackground(source: Bitmap): Bitmap {
-        val out = source.copy(Bitmap.Config.ARGB_8888, true)
-        val w = out.width
-        val h = out.height
-        val pixels = IntArray(w * h)
-        out.getPixels(pixels, 0, w, 0, 0, w, h)
+    override fun getDisplayName(context: Context): String =
+        loadMetadata(context)?.name ?: FALLBACK_DISPLAY_NAME
 
-        // 整数演算のみを使用（浮動小数点精度の問題を排除）
-        val targetColor = 0xFFfc0000.toInt()
-        val tr = (targetColor shr 16) and 0xFF
-        val tg = (targetColor shr 8)  and 0xFF
-        val tb =  targetColor         and 0xFF
-        val toleranceSq = 30 * 30  // tolerance = 30
+    override fun getDescription(context: Context): String =
+        loadMetadata(context)?.description.orEmpty()
 
-        for (i in pixels.indices) {
-            val r = Color.red(pixels[i])
-            val g = Color.green(pixels[i])
-            val b = Color.blue(pixels[i])
+    override fun getAuthor(context: Context): String =
+        loadMetadata(context)?.author.orEmpty()
 
-            val distSq = (r - tr) * (r - tr) +
-                         (g - tg) * (g - tg) +
-                         (b - tb) * (b - tb)
+    override fun getVersion(): String = cachedMetadata?.version ?: FALLBACK_VERSION
 
-            if (distSq < toleranceSq) {
-                pixels[i] = Color.TRANSPARENT
-            }
-        }
+    private fun source(): CharacterSource = CharacterSource.Assets(DEFAULT_SOURCE_PATH)
 
-        out.setPixels(pixels, 0, w, 0, 0, w, h)
-        source.recycle()
-        return out
+    private fun loadMetadata(context: Context): CharacterMetadata? {
+        cachedMetadata?.let { return it }
+        return SafeCharacterLoader(context).loadMetadata(source())?.also {
+            cachedMetadata = it
+        }
     }
-
-    override fun getDisplayName(context: Context): String = "みなと"
-
-    override fun getDescription(context: Context): String =
-        "天気・時間・季節に応じてセリフが変化するデフォルトキャラクター。"
-
-    override fun getAuthor(context: Context): String = "開発チーム"
-
-    override fun getVersion(): String = "2.0.0"
 }
```

### 変更理由

- 旧 `DefaultCharacter` へのフォールバックをやめて、`character.json` からのロードを唯一の正規ルートにしたため。
- サムネイルや表示名などの一覧表示情報も、ハードコードではなく asset JSON 側に寄せたかったため。

## 2. `DefaultCharacter.kt`

### 差分

```diff
diff --git a/app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacter.kt b/app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacter.kt
deleted file mode 100644
index bcd5de5..0000000
--- a/app/src/main/java/com/example/mascotforge/characters/default_character/DefaultCharacter.kt
+++ /dev/null
@@ -1,188 +0,0 @@
-package com.example.mascotforge.characters.default_character
-
-import android.content.Context
-import android.graphics.Bitmap
-import android.graphics.BitmapFactory
-import com.example.mascotforge.CharacterProvider
-import com.example.mascotforge.character.speech.TagParser
-import com.example.mascotforge.character.CharacterBitmapCache
-import com.example.mascotforge.speech.SpeechContext
-import java.io.BufferedReader
-import java.io.InputStreamReader
-
-class DefaultCharacter(private val context: Context) : CharacterProvider {
-
-    companion object {
-        private const val CHAR_FOLDER = "characters/default_character/images"
-        private const val SPEECH_FOLDER = "characters/default_character/speeches"
-
-        private val WEATHER_KEY_MAP = mapOf(
-            "晴れ" to "clear",
-            "曇り" to "cloudy",
-            "雨" to "rain",
-            "小雨" to "rain",
-            "雷雨" to "thunder",
-            "雪" to "snow",
-            "嵐" to "storm",
-            "台風" to "storm",
-            "霧" to "fog",
-            "霧雨" to "drizzle"
-        )
-
-        private val SPECIAL_DAY_MAP = mapOf(
-            "元日" to "new_year",
-            "クリスマス" to "christmas",
-            "クリスマスイブ" to "christmas_eve"
-        )
-    }
-
-    override val id: String = "default"
-    override val name: String = "みなと"
-
-    private enum class Emotion(val fileName: String) {
-        NORMAL("character"),
-        HAPPY("happy"),
-        EXCITED("excited"),
-        SAD("sad"),
-        ANGRY("angry"),
-        WORRIED("worried"),
-        SHY("shy"),
-        SLEEPY("sleepy")
-    }
-
-    private val shownSpeeches = mutableSetOf<String>()
-
-    @Volatile private var extractedEmotionKey: String? = null
-
-    override suspend fun getSpeech(context: SpeechContext): String? {
-        val allLines = chooseSpeechLines(context)
-        if (allLines.isEmpty()) return null
-
-        val unshown = allLines.filter { it !in shownSpeeches }
-        val raw = if (unshown.size > 1) unshown.random()
-        else {
-            shownSpeeches.clear()
-            allLines.random()
-        }
-
-        shownSpeeches += raw
-        val parsed = TagParser().parse(raw)
-        extractedEmotionKey = parsed.emotion
-        return expandVariables(parsed.cleanedText, context)
-    }
-
-    private fun expandVariables(text: String, ctx: SpeechContext): String {
-        var result = text
-        val variables = mapOf(
-            "userName" to ctx.userName,
-            "hour" to ctx.hour.toString(),
-            "minute" to ctx.minute.toString()
-        )
-        variables.forEach { (k, v) -> result = result.replace("{$k}", v) }
-        return result
-    }
-
-    private fun chooseSpeechLines(ctx: SpeechContext): List<String> {
-        val pool = mutableListOf<String>()
-        pool.addAll(loadSpeeches("default.txt"))
-
-        for (fileName in relevantFileNames(ctx)) {
-            if (assetExists(fileName)) {
-                pool.addAll(loadSpeeches(fileName))
-            }
-        }
-        return pool.distinct()
-    }
-
-    private fun relevantFileNames(ctx: SpeechContext): List<String> {
-        val slot = ctx.timeSlot
-        val weather = WEATHER_KEY_MAP.entries
-            .firstOrNull { ctx.weatherCode.contains(it.key) }
-            ?.value ?: "clear"
-
-        return listOf(
-            "$slot.txt",
-            "$weather.txt",
-            "${weather}_$slot.txt"
-        )
-    }
-
-    private fun assetExists(fileName: String): Boolean = try {
-        context.assets.open("$SPEECH_FOLDER/$fileName").close()
-        true
-    } catch (_: Exception) { false }
-
-    private fun loadSpeeches(fileName: String): List<String> = try {
-        context.assets.open("$SPEECH_FOLDER/$fileName").use { input ->
-            BufferedReader(InputStreamReader(input, Charsets.UTF_8))
-                .lineSequence()
-                .map { it.trim() }
-                .filter { it.isNotEmpty() && !it.startsWith("#") }
-                .toList()
-        }
-    } catch (_: Exception) { emptyList() }
-
-    override fun getCharaImage(context: SpeechContext): Bitmap? {
-        val tagEmotion = extractedEmotionKey
-            ?.let { key -> Emotion.entries.find { it.fileName == key } }
-        extractedEmotionKey = null
-
-        val emotion = tagEmotion ?: Emotion.NORMAL
-        val cacheKey = "default:${emotion.fileName}"
-        
-        CharacterBitmapCache.get(cacheKey)?.let { return it }
-        
-        val loaded = loadImage(emotion)
-        if (loaded != null) {
-            CharacterBitmapCache.put(cacheKey, loaded)
-        }
-        return loaded
-    }
-
-    private fun loadImage(emotion: Emotion): Bitmap? = try {
-        context.assets.open("$CHAR_FOLDER/${emotion.fileName}.webp").use { stream ->
-            val src = BitmapFactory.decodeStream(stream) ?: return null
-            removeRedBackground(src, targetColor = 0xFFfc0000.toInt(), tolerance = 30)
-        }
-    } catch (_: Exception) { null }
-
-    private fun removeRedBackground(src: Bitmap, targetColor: Int, tolerance: Int): Bitmap {
-        val tr = (targetColor shr 16) and 0xFF
-        val tg = (targetColor shr 8)  and 0xFF
-        val tb =  targetColor         and 0xFF
-
-        val pixels = IntArray(src.width * src.height)
-        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
-
-        val toleranceSq = tolerance * tolerance
-
-        for (i in pixels.indices) {
-            val r = (pixels[i] shr 16) and 0xFF
-            val g = (pixels[i] shr 8)  and 0xFF
-            val b =  pixels[i]         and 0xFF
-
-            val distSq = (r - tr) * (r - tr) +
-                         (g - tg) * (g - tg) +
-                         (b - tb) * (b - tb)
-
-            if (distSq < toleranceSq) pixels[i] = 0x00000000
-        }
-
-        val result = src.copy(Bitmap.Config.ARGB_8888, true)
-        result.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
-        src.recycle()
-        return result
-    }
-
-    override fun isAvailable(): Boolean = true
-
-    fun clearCache() {
-    }
-}
```

### 変更理由

- ハードコード版 default キャラ実装そのものを撤去して、今後は JSON 駆動だけに揃えるため。

## 3. `CharacterPreferences.kt`

### 差分

```diff
diff --git a/app/src/main/java/com/example/mascotforge/CharacterPreferences.kt b/app/src/main/java/com/example/mascotforge/CharacterPreferences.kt
index 080ac82..15bf559 100644
--- a/app/src/main/java/com/example/mascotforge/CharacterPreferences.kt
+++ b/app/src/main/java/com/example/mascotforge/CharacterPreferences.kt
@@ -8,53 +8,50 @@ object CharacterPreferences {
     private const val PREF_NAME = "character_settings"
     private const val KEY_SELECTED_CHARACTER = "selected_character_id"
     private const val KEY_WIDGET_CHARACTER_PREFIX = "widget_character_"
+    private const val LEGACY_DEFAULT_CHARACTER_ID = "default_character"
 
     private fun getPrefs(context: Context): SharedPreferences {
         return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
     }
 
     fun getSelectedCharacterId(context: Context): String {
-        return getPrefs(context).getString(
+        val prefs = getPrefs(context)
+        val characterId = prefs.getString(
             KEY_SELECTED_CHARACTER,
             CharacterRegistry.getDefaultCharacterId()
         ) ?: CharacterRegistry.getDefaultCharacterId()
+        val normalized = normalizeCharacterId(characterId)
+        if (normalized != characterId) {
+            setSelectedCharacterId(context, normalized)
+        }
+        return normalized
     }
 
     fun setSelectedCharacterId(context: Context, characterId: String) {
         getPrefs(context).edit()
             .putString(KEY_SELECTED_CHARACTER, characterId)
             .apply()
     }
 
     fun getCharacterIdForWidget(context: Context, widgetId: Int): String {
         val prefs = getPrefs(context)
-        return prefs.getString(
+        val characterId = prefs.getString(
             "$KEY_WIDGET_CHARACTER_PREFIX$widgetId",
             null
-        ) ?: getSelectedCharacterId(context)
+        ) ?: getSelectedCharacterId(context)
+        val normalized = normalizeCharacterId(characterId)
+        if (normalized != characterId) {
+            setCharacterIdForWidget(context, widgetId, normalized)
+        }
+        return normalized
     }
 
     fun setCharacterIdForWidget(context: Context, widgetId: Int, characterId: String) {
         getPrefs(context).edit()
             .putString("$KEY_WIDGET_CHARACTER_PREFIX$widgetId", characterId)
             .apply()
     }
 
     fun setCharacterIdForWidgets(context: Context, widgetIds: List<Int>, characterId: String) {
         val editor = getPrefs(context).edit()
         widgetIds.forEach { widgetId ->
             editor.putString("$KEY_WIDGET_CHARACTER_PREFIX$widgetId", characterId)
         }
         editor.apply()
     }
+
+    private fun normalizeCharacterId(characterId: String): String {
+        return if (characterId == LEGACY_DEFAULT_CHARACTER_ID) {
+            CharacterRegistry.getDefaultCharacterId()
+        } else {
+            characterId
+        }
+    }
 }
```

### 変更理由

- 既存端末に `default_character` が保存されていても、新しい正規 ID の `default` に自動で寄せるため。
- 旧 ID が残ったままだと JSON 駆動 default の初期選択に失敗する可能性があったため。

## 4. `WidgetCharacterConfig.kt`

### 差分

```diff
diff --git a/app/src/main/java/widget/WidgetCharacterConfig.kt b/app/src/main/java/widget/WidgetCharacterConfig.kt
index 3ea3c96..7f385e4 100644
--- a/app/src/main/java/widget/WidgetCharacterConfig.kt
+++ b/app/src/main/java/widget/WidgetCharacterConfig.kt
@@ -3,12 +3,8 @@ package com.example.mascotforge
 import android.content.Context
 import android.content.SharedPreferences
 import android.util.Log
+import com.example.mascotforge.characters.CharacterRegistry
 
 class WidgetCharacterConfig(private val context: Context) {
 
     companion object {
         private const val TAG = "WidgetCharacterConfig"
         private const val PREFS_NAME = "widget_character_config"
         private const val KEY_PREFIX_CHARACTER = "widget_char_"
         private const val KEY_DEFAULT_CHARACTER = "default_character_id"
-        private const val FALLBACK_CHARACTER_ID = "default_character"
+        private const val LEGACY_DEFAULT_CHARACTER_ID = "default_character"
+        private val FALLBACK_CHARACTER_ID = CharacterRegistry.getDefaultCharacterId()
     }
 
     private val prefs: SharedPreferences =
         context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
 
     fun getCharacterForWidget(widgetId: Int): String {
         val key = "$KEY_PREFIX_CHARACTER$widgetId"
         val characterId = prefs.getString(key, null)
 
         if (characterId != null) {
-            Log.d(TAG, "Widget #$widgetId uses: $characterId")
-            return characterId
+            val normalized = normalizeCharacterId(characterId)
+            if (normalized != characterId) {
+                setCharacterForWidget(widgetId, normalized)
+            }
+            Log.d(TAG, "Widget #$widgetId uses: $normalized")
+            return normalized
         }
 
         val defaultCharId = getDefaultCharacter()
         Log.d(TAG, "Widget #$widgetId uses default: $defaultCharId")
         return defaultCharId
     }
 
     fun setCharacterForWidget(widgetId: Int, characterId: String) {
         val key = "$KEY_PREFIX_CHARACTER$widgetId"
         prefs.edit().putString(key, characterId).apply()
-        Log.i(TAG, "Widget #$widgetId → $characterId")
+        Log.i(TAG, "Widget #$widgetId -> $characterId")
     }
 
     fun getDefaultCharacter(): String {
-        return prefs.getString(KEY_DEFAULT_CHARACTER, FALLBACK_CHARACTER_ID)
+        val characterId = prefs.getString(KEY_DEFAULT_CHARACTER, FALLBACK_CHARACTER_ID)
             ?: FALLBACK_CHARACTER_ID
+        val normalized = normalizeCharacterId(characterId)
+        if (normalized != characterId) {
+            setDefaultCharacter(normalized)
+        }
+        return normalized
     }
 
     fun setDefaultCharacter(characterId: String) {
         prefs.edit().putString(KEY_DEFAULT_CHARACTER, characterId).apply()
-        Log.i(TAG, "Default character → $characterId")
+        Log.i(TAG, "Default character -> $characterId")
     }
@@ -86,7 +71,7 @@ class WidgetCharacterConfig(private val context: Context) {
             if (key.startsWith(KEY_PREFIX_CHARACTER) && value is String) {
                 val widgetId = key.removePrefix(KEY_PREFIX_CHARACTER).toIntOrNull()
                 if (widgetId != null) {
-                    configs[widgetId] = value
+                    configs[widgetId] = normalizeCharacterId(value)
                 }
             }
         }
@@ -94,13 +79,18 @@ class WidgetCharacterConfig(private val context: Context) {
         return configs
     }
 
     fun getWidgetsUsingCharacter(characterId: String): List<Int> {
         return getAllWidgetConfigs()
             .filterValues { it == characterId }
             .keys
             .toList()
     }
+
+    private fun normalizeCharacterId(characterId: String): String {
+        return if (characterId == LEGACY_DEFAULT_CHARACTER_ID) {
+            FALLBACK_CHARACTER_ID
+        } else {
+            characterId
+        }
+    }
 }
```

### 変更理由

- ウィジェット側の既定 ID が旧 `default_character` のままだったので、JSON 駆動の `default` と整合させるため。
- 既存設定が残っていても自動補正されるようにして、設定移行の事故を減らすため。

## 変更の意図まとめ

- `default` キャラの読み込み元を「ハードコード実装」と「JSON 実装」の二重管理から、`character.json` を正にする単一路線へ寄せた。
- それに合わせて、保存済み設定の旧 ID `default_character` を `default` に吸収する移行処理を `CharacterPreferences` と `WidgetCharacterConfig` に入れた。
- 最終的に、`DefaultCharacter.kt` は削除し、`DefaultCharacterFactory.kt` も JSON 読み込み専用にした。
