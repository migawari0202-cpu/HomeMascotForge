package com.example.mascotforge.characters

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.characters.evil.EvilCharacterFactory
import com.example.mascotforge.characters.default_character.DefaultCharacterFactory
import com.example.mascotforge.character.CharacterSource
import com.example.mascotforge.character.SafeCharacterLoader
import java.io.File

/**
 * キャラクター統合レジストリ v2
 *
 * 【管理対象】
 * 1. APK同梱キャラ（旧来のFactory方式）
 * 2. ZIPインストールキャラ（filesDir）
 */
object CharacterRegistry {

    private const val TAG = "CharacterRegistry"

    /**
     * APK同梱の旧Factory一覧（後方互換性）
     */
    private fun getBuiltInFactories(): List<CharacterFactory> {
        return listOf(
            EvilCharacterFactory()
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

        // 2. Assets内のZIP形式キャラ（初期状態で利用可能）
        factories.addAll(getAssetsCharacterFactories(context))

        // 3. ユーザーがインストールしたZIPキャラ
        factories.addAll(getInstalledFactories(context))

        Log.d(TAG, "Total factories: ${factories.size} (BuiltIn: ${getBuiltInFactories().size}, Assets: ${getAssetsCharacterFactories(context).size}, Installed: ${getInstalledFactories(context).size})")

        return factories
    }

    /**
     * Assets内のZIP形式キャラを取得（初期状態で利用可能）
     */
    private fun getAssetsCharacterFactories(context: Context): List<CharacterFactory> {
        return try {
            val factories = mutableListOf<CharacterFactory>()
            
            // defaultキャラはDefaultCharacterFactoryで提供
            factories.add(DefaultCharacterFactory())
            
            Log.d(TAG, "Loaded assets character: default (DefaultCharacterFactory)")
            factories
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load assets characters", e)
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
                            CharacterSource.InstalledFiles("installed_characters/$charId")
                        )

                        if (meta != null) {
                            Log.d(TAG, "Loaded installed character: $charId")
                            InstalledCharacterFactory(
                                meta,
                                context,
                                CharacterSource.InstalledFiles("installed_characters/$charId")
                            )
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
    fun getDefaultCharacterId(): String = "default"

    /**
     * デフォルトキャラを取得
     */
    fun getDefaultCharacter(context: Context): CharacterProvider {
        return getCharacterById(context, getDefaultCharacterId())
            ?: getFactories(context).firstOrNull()?.create(context)
            ?: error("No characters available")
    }
}

/**
 * キャラクター用Factory（ZIP形式、Assets/Files両対応）
 */
class InstalledCharacterFactory(
    private val metadata: com.example.mascotforge.character.CharacterMetadata,
    private val context: Context,
    private val source: com.example.mascotforge.character.CharacterSource = com.example.mascotforge.character.CharacterSource.InstalledFiles("installed_characters/${metadata.id}")
) : CharacterFactory {

    override fun getCharacterId(): String = metadata.id

    override fun getDisplayName(context: Context): String = metadata.name

    override fun getDescription(context: Context): String = metadata.description

    override fun getAuthor(context: Context): String = metadata.author

    override fun getVersion(): String = metadata.version

    override fun create(context: Context): CharacterProvider {
        val loader = SafeCharacterLoader(context)
        return loader.loadCharacter(source)
            ?: throw IllegalStateException("Failed to load character: ${metadata.id}")
    }

    override fun getThumbnail(context: Context): Drawable? {
        return try {
            val imageName = metadata.imageMapping.values.firstOrNull()

            val imageFile = when (source) {
                is com.example.mascotforge.character.CharacterSource.Assets -> {
                    // assetsから読み込む場合はファイルパスではなく、assetsから直接取得する
                    return try {
                        val assetPath = "${source.basePath}/images/${imageName ?: "character.webp"}"
                        context.assets.open(assetPath).use { input ->
                            android.graphics.BitmapFactory.decodeStream(input)?.let {
                                android.graphics.drawable.BitmapDrawable(context.resources, it)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("InstalledFactory", "Failed to load thumbnail from assets: ${metadata.id}", e)
                        null
                    }
                }
                is com.example.mascotforge.character.CharacterSource.InstalledFiles -> {
                    if (imageName != null) {
                        File(context.filesDir, "${source.basePath}/images/$imageName")
                    } else {
                        File(context.filesDir, "${source.basePath}/images/character.png")
                    }
                }
            }

            if (imageFile is File && imageFile.exists()) {
                return android.graphics.drawable.Drawable.createFromPath(imageFile.absolutePath)
            }

            // フォールバック: imagesフォルダ内の最初の画像を使用
            if (source is com.example.mascotforge.character.CharacterSource.InstalledFiles) {
                val imagesDir = File(context.filesDir, "${source.basePath}/images")
                val firstImage = imagesDir.listFiles()
                    ?.firstOrNull {
                        it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")
                    }

                if (firstImage != null) {
                    Log.d("InstalledFactory", "Using first available image: ${firstImage.name}")
                    return android.graphics.drawable.Drawable.createFromPath(firstImage.absolutePath)
                }
            }

            Log.w("InstalledFactory", "No thumbnail found for: ${metadata.id}")
            null
        } catch (e: Exception) {
            Log.w("InstalledFactory", "Failed to load thumbnail: ${metadata.id}", e)
            null
        }
    }
}