package com.example.mascotforge.characters

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.character.CharacterMetadata
import com.example.mascotforge.character.CharacterSource
import com.example.mascotforge.character.DynamicCharacter
import com.example.mascotforge.character.SafeCharacterLoader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CharacterRegistry {

    private const val TAG = "CharacterRegistry"

    @Volatile
    private var entriesCache: List<CharacterEntry>? = null

    private val characterInstanceCache = ConcurrentHashMap<String, CharacterProvider>()
    private val lock = Any()

    /**
     * インストール / アンインストール / 定義更新時に呼ぶ。
     * メタデータと DynamicCharacter インスタンスの両方を破棄する。
     */
    fun invalidate(characterId: String? = null) {
        synchronized(lock) {
            if (characterId == null) {
                entriesCache = null
                characterInstanceCache.clear()
                Log.d(TAG, "Registry cache fully invalidated")
            } else {
                // エントリ一覧はディレクトリ構成が変わっている可能性があるので丸ごと捨てる
                entriesCache = null
                characterInstanceCache.remove(characterId)
                Log.d(TAG, "Registry cache invalidated for: $characterId")
            }
        }
    }

    fun getFactories(context: Context): List<CharacterFactory> {
        return getEntries(context).map { it.factory }
    }

    fun getEntries(context: Context): List<CharacterEntry> {
        entriesCache?.let { return it }
        synchronized(lock) {
            entriesCache?.let { return it }
            val appCtx = context.applicationContext
            val assetsEntries = getAssetsEntries(appCtx)
            val assetIds = assetsEntries.map { it.metadata.id }.toSet()
            val installedEntries = getInstalledEntriesInternal(appCtx)
                .filterNot { entry ->
                    val isDuplicate = entry.metadata.id in assetIds
                    if (isDuplicate) {
                        Log.w(TAG, "Skipping installed character with built-in duplicate id: ${entry.metadata.id}")
                    }
                    isDuplicate
                }

            val all = buildList {
                addAll(assetsEntries)
                addAll(installedEntries)
            }
            entriesCache = all
            Log.d(
                TAG,
                "Cached characters: ${all.size} (Assets: ${assetsEntries.size}, Installed: ${installedEntries.size})"
            )
            return all
        }
    }

    fun getInstalledEntries(context: Context): List<CharacterEntry> {
        return getEntries(context).filterNot { it.isBuiltIn }
    }

    fun getInstalledMetadata(context: Context): List<CharacterMetadata> {
        return getInstalledEntries(context).map { it.metadata }
    }

    private fun getAssetsEntries(context: Context): List<CharacterEntry> {
        return try {
            val loader = SafeCharacterLoader(context)
            val characterDirs = context.assets.list("characters")?.toList().orEmpty()

            characterDirs.mapNotNull { dirName ->
                val source = CharacterSource.Assets("characters/$dirName")
                try {
                    val meta = loader.loadMetadata(source)
                    if (meta != null) {
                        Log.d(TAG, "Loaded assets character: ${meta.id} from $dirName")
                        val factory = InstalledCharacterFactory(meta, context, source)
                        CharacterEntry(meta, source, isBuiltIn = true, factory = factory)
                    } else {
                        Log.w(TAG, "Failed to load assets metadata from $dirName")
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create assets factory for: $dirName", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load assets characters", e)
            emptyList()
        }
    }

    private fun getInstalledEntriesInternal(context: Context): List<CharacterEntry> {
        return try {
            val installedDir = File(context.filesDir, "installed_characters")

            if (!installedDir.exists() || !installedDir.isDirectory) {
                Log.d(TAG, "No installed characters directory")
                return emptyList()
            }

            val loader = SafeCharacterLoader(context)

            installedDir.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { charDir ->
                    try {
                        val charId = charDir.name
                        val source = CharacterSource.InstalledFiles("installed_characters/$charId")
                        val meta = loader.loadMetadata(source)

                        if (meta != null) {
                            Log.d(TAG, "Loaded installed character: $charId")
                            val factory = InstalledCharacterFactory(meta, context, source)
                            CharacterEntry(meta, source, isBuiltIn = false, factory = factory)
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

    fun getInternalCharacters(context: Context): List<CharacterProvider> {
        return getEntries(context).mapNotNull { entry ->
            try {
                getCharacterById(context, entry.metadata.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create character: ${entry.metadata.id}", e)
                null
            }
        }
    }

    fun hasCharacter(context: Context, id: String): Boolean {
        return getEntries(context).any { it.metadata.id == id }
    }

    fun getCharacterById(context: Context, id: String): CharacterProvider? {
        characterInstanceCache[id]?.let { return it }

        val entry = getEntries(context).find { it.metadata.id == id } ?: return null
        return characterInstanceCache.getOrPut(id) {
            entry.factory.create(context.applicationContext)
        }
    }

    fun getDefaultCharacterId(context: Context): String {
        return getEntries(context).firstOrNull()?.metadata?.id
            ?: error("No characters available")
    }

    fun getDefaultCharacter(context: Context): CharacterProvider {
        val id = getDefaultCharacterId(context)
        return getCharacterById(context, id)
            ?: error("No characters available")
    }
}

data class CharacterEntry(
    val metadata: CharacterMetadata,
    val source: CharacterSource,
    val isBuiltIn: Boolean,
    val factory: CharacterFactory
)

class InstalledCharacterFactory(
    private val metadata: CharacterMetadata,
    private val context: Context,
    private val source: CharacterSource =
        CharacterSource.InstalledFiles("installed_characters/${metadata.id}")
) : CharacterFactory {

    override fun getCharacterId(): String = metadata.id

    override fun getDisplayName(context: Context): String = metadata.name

    override fun getDescription(context: Context): String = metadata.description

    override fun getAuthor(context: Context): String = metadata.author

    override fun getVersion(): String = metadata.version

    /**
     * Registry で既にパース済みの metadata を使い、character.json を再読込しない。
     */
    override fun create(context: Context): CharacterProvider {
        val appCtx = context.applicationContext
        return SafeCharacterLoader(appCtx).createFromMetadata(source, metadata)
            ?: throw IllegalStateException("Failed to load character: ${metadata.id}")
    }

    override fun getThumbnail(context: Context): Drawable? {
        return try {
            val candidates = listOfNotNull(
                metadata.imageMapping["thumbnail"],
                "thumbnail.png",
                metadata.imageMapping["normal"],
                metadata.imageMapping["character"],
                metadata.imageMapping.values.firstOrNull(),
                "character.png",
                "character.webp"
            ).distinct()

            val imageFile = when (source) {
                is CharacterSource.Assets -> {
                    for (imageName in candidates) {
                        try {
                            val assetPath = "${source.basePath}/images/$imageName"
                            context.assets.open(assetPath).use { input ->
                                android.graphics.BitmapFactory.decodeStream(input)?.let {
                                    return android.graphics.drawable.BitmapDrawable(context.resources, it)
                                }
                            }
                        } catch (_: Exception) {
                            // Try the next thumbnail candidate.
                        }
                    }

                    Log.w("InstalledFactory", "Failed to load thumbnail from assets: ${metadata.id}")
                    return null
                }

                is CharacterSource.InstalledFiles -> {
                    candidates
                        .map { File(context.filesDir, "${source.basePath}/images/$it") }
                        .firstOrNull { it.exists() }
                }
            }

            if (imageFile != null && imageFile.exists()) {
                return Drawable.createFromPath(imageFile.absolutePath)
            }

            if (source is CharacterSource.InstalledFiles) {
                val imagesDir = File(context.filesDir, "${source.basePath}/images")
                val firstImage = imagesDir.listFiles()
                    ?.firstOrNull {
                        it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")
                    }

                if (firstImage != null) {
                    Log.d("InstalledFactory", "Using first available image: ${firstImage.name}")
                    return Drawable.createFromPath(firstImage.absolutePath)
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
