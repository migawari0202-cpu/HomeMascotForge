package com.example.mascotforge.characters

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.characters.default_character.DefaultCharacterFactory
import com.example.mascotforge.characters.evil.EvilCharacterFactory
import com.mascotforge.character.SafeCharacterLoader
import com.mascotforge.installer.CharacterInstaller
import java.io.File

/**
 * キャラクター統合レジストリ v2
 *
 * 【管理対象】
 * 1. APK同梱キャラ（旧来のFactory方式）
 * 2. assets/characters/ のJSONキャラ
 * 3. ZIPインストールキャラ（filesDir）
 */
object CharacterRegistry {

    private const val TAG = "CharacterRegistry"

    /**
     * APK同梱の旧Factory一覧（後方互換性）
     */
    private fun getBuiltInFactories(): List<CharacterFactory> {
        return listOf(
            DefaultCharacterFactory(),
            EvilCharacterFactory()
            // 新しい旧形式キャラはここに追加
        )
    }

    /**
     * 全てのキャラクターFactory取得（UI表示用）
     *
     * @return APK同梱 + assets JSON + インストール済みの統合リスト
     */
    fun getFactories(context: Context): List<CharacterFactory> {
        val factories = mutableListOf<CharacterFactory>()

        // 1. APK同梱キャラ（旧Factory方式）
        factories.addAll(getBuiltInFactories())

        // 2. assets/characters/ のJSONキャラ
        factories.addAll(getAssetsJsonFactories(context))

        // 3. ZIPインストールキャラ
        factories.addAll(getInstalledFactories(context))

        Log.d(TAG, "Total factories: ${factories.size} (BuiltIn: ${getBuiltInFactories().size}, Assets: ${getAssetsJsonFactories(context).size}, Installed: ${getInstalledFactories(context).size})")

        return factories
    }

    /**
     * assets/characters/ のJSONキャラを取得
     */
    private fun getAssetsJsonFactories(context: Context): List<CharacterFactory> {
        return try {
            val charIds = context.assets.list("characters") ?: emptyArray()
            val loader = SafeCharacterLoader(context)

            charIds.mapNotNull { charId ->
                try {
                    // character.json が存在するかチェック
                    val jsonPath = "characters/$charId/character.json"

                    // ファイルの存在チェック（openを試みる）
                    val jsonExists = try {
                        context.assets.open(jsonPath).use { it }
                        true
                    } catch (e: java.io.FileNotFoundException) {
                        Log.v(TAG, "Skipping $charId (not a JSON character)")
                        false
                    }

                    if (!jsonExists) {
                        return@mapNotNull null
                    }

                    val meta = loader.loadMetadata("characters/$charId", isAssets = true)
                    if (meta != null) {
                        Log.d(TAG, "Loaded JSON character: $charId")
                        AssetsJsonCharacterFactory(charId, meta, context)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load assets character: $charId", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list assets characters", e)
            emptyList()
        }
    }

    /**
     * ZIPインストールキャラを取得
     *
     * 【変更点】
     * - CharacterInstaller のメタデータは使わず、
     *   直接 SafeCharacterLoader でロードする
     */
    private fun getInstalledFactories(context: Context): List<CharacterFactory> {
        return try {
            val installedDir = File(context.filesDir, "installed_characters")

            if (!installedDir.exists() || !installedDir.isDirectory) {
                Log.d(TAG, "No installed characters directory")
                return emptyList()
            }

            val loader = SafeCharacterLoader(context)

            // installed_characters/ 内のフォルダを列挙
            installedDir.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { charDir ->
                    try {
                        val charId = charDir.name
                        val meta = loader.loadMetadata(
                            "installed_characters/$charId",
                            isAssets = false
                        )

                        if (meta != null) {
                            Log.d(TAG, "Loaded installed character: $charId")
                            InstalledCharacterFactory(meta, context)
                        } else {
                            Log.w(TAG, "Failed to load metadata for: $charId")
                            null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create factory for: ${charDir.name}", e)
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load installed characters", e)
            emptyList()
        }
    }

    /**
     * 全キャラクターの一覧を取得
     */
    fun getInternalCharacters(context: Context): List<CharacterProvider> {
        return getFactories(context).mapNotNull { factory ->
            try {
                factory.create(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create character: ${factory.getCharacterId()}", e)
                null
            }
        }
    }

    /**
     * 特定IDのキャラが存在するかチェック
     */
    fun hasCharacter(context: Context, id: String): Boolean {
        return getFactories(context).any { it.getCharacterId() == id }
    }

    /**
     * IDを指定してキャラを取得
     */
    fun getCharacterById(context: Context, id: String): CharacterProvider? {
        return getFactories(context)
            .find { it.getCharacterId() == id }
            ?.create(context)
    }

    /**
     * デフォルトキャラのIDを取得
     */
    fun getDefaultCharacterId(): String = "default_character"

    /**
     * デフォルトキャラを取得
     */
    fun getDefaultCharacter(context: Context): CharacterProvider {
        return getCharacterById(context, getDefaultCharacterId())
            ?: DefaultCharacterFactory().create(context)
    }
}

/**
 * assets/characters/ のJSONキャラ用Factory
 */
class AssetsJsonCharacterFactory(
    private val charId: String,
    private val metadata: com.mascotforge.character.CharacterMetadata,
    private val context: Context
) : CharacterFactory {

    override fun getCharacterId(): String = charId

    override fun getDisplayName(context: Context): String = metadata.name

    override fun getDescription(context: Context): String = metadata.description

    override fun getAuthor(context: Context): String = metadata.author

    override fun getVersion(): String = metadata.version

    override fun create(context: Context): CharacterProvider {
        val loader = SafeCharacterLoader(context)
        return loader.loadCharacter("characters/$charId", isAssets = true)
            ?: throw IllegalStateException("Failed to load assets character: $charId")
    }

    override fun getThumbnail(context: Context): Drawable? {
        return try {
            // 1. imageMapping から最初の画像を取得
            val imageName = metadata.imageMapping.values.firstOrNull()

            // 2. imageName があればそれを使用
            if (imageName != null) {
                try {
                    val stream = context.assets.open("characters/$charId/images/$imageName")
                    return Drawable.createFromStream(stream, imageName)
                } catch (e: Exception) {
                    Log.d("AssetsJsonFactory", "Image not found: $imageName")
                }
            }

            // 3. フォールバック: character.png
            try {
                val stream = context.assets.open("characters/$charId/images/character.png")
                return Drawable.createFromStream(stream, "character.png")
            } catch (e: Exception) {
                Log.d("AssetsJsonFactory", "character.png not found")
            }

            // 4. 最後のフォールバック: imagesフォルダの最初の画像
            val imageFiles = context.assets.list("characters/$charId/images") ?: emptyArray()
            val firstImage = imageFiles.firstOrNull {
                it.endsWith(".png", ignoreCase = true) ||
                        it.endsWith(".jpg", ignoreCase = true) ||
                        it.endsWith(".jpeg", ignoreCase = true) ||
                        it.endsWith(".webp", ignoreCase = true)
            }

            if (firstImage != null) {
                Log.d("AssetsJsonFactory", "Using first available image: $firstImage")
                val stream = context.assets.open("characters/$charId/images/$firstImage")
                Drawable.createFromStream(stream, firstImage)
            } else {
                Log.w("AssetsJsonFactory", "No images found for: $charId")
                null
            }
        } catch (e: Exception) {
            Log.w("AssetsJsonFactory", "Failed to load thumbnail: $charId", e)
            null
        }
    }
}

/**
 * ZIPインストールキャラ用Factory
 */
class InstalledCharacterFactory(
    private val metadata: com.mascotforge.character.CharacterMetadata,
    private val context: Context
) : CharacterFactory {

    override fun getCharacterId(): String = metadata.id

    override fun getDisplayName(context: Context): String = metadata.name

    override fun getDescription(context: Context): String = metadata.description

    override fun getAuthor(context: Context): String = metadata.author

    override fun getVersion(): String = metadata.version

    override fun create(context: Context): CharacterProvider {
        val loader = SafeCharacterLoader(context)
        return loader.loadCharacter("installed_characters/${metadata.id}", isAssets = false)
            ?: throw IllegalStateException("Failed to load installed character: ${metadata.id}")
    }

    override fun getThumbnail(context: Context): Drawable? {
        return try {
            // 1. imageMapping から最初の画像を取得
            val imageName = metadata.imageMapping.values.firstOrNull()

            // 2. imageName があればそれを使用
            val imageFile = if (imageName != null) {
                File(context.filesDir, "installed_characters/${metadata.id}/images/$imageName")
            } else {
                // フォールバック: character.png
                File(context.filesDir, "installed_characters/${metadata.id}/images/character.png")
            }

            if (imageFile.exists()) {
                return Drawable.createFromPath(imageFile.absolutePath)
            }

            // 3. どちらもなければ、imagesフォルダ内の最初の画像を使用
            val imagesDir = File(context.filesDir, "installed_characters/${metadata.id}/images")
            val firstImage = imagesDir.listFiles()
                ?.firstOrNull {
                    it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")
                }

            if (firstImage != null) {
                Log.d("InstalledFactory", "Using first available image: ${firstImage.name}")
                Drawable.createFromPath(firstImage.absolutePath)
            } else {
                Log.w("InstalledFactory", "No images found in: ${imagesDir.path}")
                null
            }
        } catch (e: Exception) {
            Log.w("InstalledFactory", "Failed to load thumbnail: ${metadata.id}", e)
            null
        }
    }
}