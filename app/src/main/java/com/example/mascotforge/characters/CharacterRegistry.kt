package com.example.mascotforge.characters

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider
import com.example.mascotforge.character.CharacterMetadata
import com.example.mascotforge.character.CharacterSource
import com.example.mascotforge.character.SafeCharacterLoader
import java.io.File

object CharacterRegistry {

    private const val TAG = "CharacterRegistry"

    fun getFactories(context: Context): List<CharacterFactory> {
        return getEntries(context).map { it.factory }
    }

    fun getEntries(context: Context): List<CharacterEntry> {
        val assetsEntries = getAssetsEntries(context)
        val assetIds = assetsEntries.map { it.metadata.id }.toSet()
        val installedEntries = getInstalledEntries(context)
            .filterNot { entry ->
                val isDuplicate = entry.metadata.id in assetIds
                if (isDuplicate) {
                    Log.w(TAG, "Skipping installed character with built-in duplicate id: ${entry.metadata.id}")
                }
                isDuplicate
            }

        return buildList {
            addAll(assetsEntries)
            addAll(installedEntries)
        }.also {
            Log.d(
                TAG,
                "Total characters: ${it.size} (Assets: ${assetsEntries.size}, Installed: ${installedEntries.size})"
            )
        }
    }

    fun getInstalledEntries(context: Context): List<CharacterEntry> {
        return getInstalledEntriesInternal(context)
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
                        val meta = loader.loadMetadata(
                            CharacterSource.InstalledFiles("installed_characters/$charId")
                        )

                        if (meta != null) {
                            Log.d(TAG, "Loaded installed character: $charId")
                            val source = CharacterSource.InstalledFiles("installed_characters/$charId")
                            val factory = InstalledCharacterFactory(
                                meta,
                                context,
                                source
                            )
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
        return getFactories(context).mapNotNull { factory ->
            try {
                factory.create(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create character: ${factory.getCharacterId()}", e)
                null
            }
        }
    }

    fun hasCharacter(context: Context, id: String): Boolean {
        return getFactories(context).any { it.getCharacterId() == id }
    }

    fun getCharacterById(context: Context, id: String): CharacterProvider? {
        return getFactories(context)
            .find { it.getCharacterId() == id }
            ?.create(context)
    }

    fun getDefaultCharacterId(context: Context): String {
        return getFactories(context).firstOrNull()?.getCharacterId()
            ?: error("No characters available")
    }

    fun getDefaultCharacter(context: Context): CharacterProvider {
        return getFactories(context).firstOrNull()?.create(context)
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
    private val source: com.example.mascotforge.character.CharacterSource =
        com.example.mascotforge.character.CharacterSource.InstalledFiles("installed_characters/${metadata.id}")
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
