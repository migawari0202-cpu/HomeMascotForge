package com.example.mascotforge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.mascotforge.characters.CharacterRegistry

class WidgetCharacterConfig(private val context: Context) {

    companion object {
        private const val TAG = "WidgetCharacterConfig"
        private const val PREFS_NAME = "widget_character_config"
        private const val KEY_PREFIX_CHARACTER = "widget_char_"
        private const val KEY_DEFAULT_CHARACTER = "default_character_id"
        private const val REMOVED_DEFAULT_CHARACTER_ID = "default"
        private const val LEGACY_DEFAULT_CHARACTER_ID = "default_character"
        private const val REMOVED_EVIL_CHARACTER_ID = "evil"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCharacterForWidget(widgetId: Int): String {
        val key = "$KEY_PREFIX_CHARACTER$widgetId"
        val characterId = prefs.getString(key, null)

        if (characterId != null) {
            val normalized = normalizeCharacterId(characterId)
            if (normalized != characterId) {
                setCharacterForWidget(widgetId, normalized)
            }
            Log.d(TAG, "Widget #$widgetId uses: $normalized")
            return normalized
        }

        val defaultCharId = getDefaultCharacter()
        Log.d(TAG, "Widget #$widgetId uses default: $defaultCharId")
        return defaultCharId
    }

    fun setCharacterForWidget(widgetId: Int, characterId: String) {
        val key = "$KEY_PREFIX_CHARACTER$widgetId"
        prefs.edit().putString(key, characterId).apply()
        Log.i(TAG, "Widget #$widgetId -> $characterId")
    }

    fun getDefaultCharacter(): String {
        val fallbackId = CharacterRegistry.getDefaultCharacterId(context)
        val characterId = prefs.getString(KEY_DEFAULT_CHARACTER, fallbackId) ?: fallbackId
        val normalized = normalizeCharacterId(characterId)
        if (normalized != characterId) {
            setDefaultCharacter(normalized)
        }
        return normalized
    }

    fun setDefaultCharacter(characterId: String) {
        prefs.edit().putString(KEY_DEFAULT_CHARACTER, characterId).apply()
        Log.i(TAG, "Default character -> $characterId")
    }

    fun clearWidgetConfig(widgetId: Int) {
        val key = "$KEY_PREFIX_CHARACTER$widgetId"
        prefs.edit().remove(key).apply()
        Log.i(TAG, "Widget #$widgetId config cleared")
    }

    fun getAllWidgetConfigs(): Map<Int, String> {
        val configs = mutableMapOf<Int, String>()

        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX_CHARACTER) && value is String) {
                val widgetId = key.removePrefix(KEY_PREFIX_CHARACTER).toIntOrNull()
                if (widgetId != null) {
                    configs[widgetId] = normalizeCharacterId(value)
                }
            }
        }

        return configs
    }

    fun getWidgetsUsingCharacter(characterId: String): List<Int> {
        return getAllWidgetConfigs()
            .filterValues { it == characterId }
            .keys
            .toList()
    }

    private fun normalizeCharacterId(characterId: String): String {
        return if (
            characterId == LEGACY_DEFAULT_CHARACTER_ID ||
            characterId == REMOVED_DEFAULT_CHARACTER_ID ||
            characterId == REMOVED_EVIL_CHARACTER_ID
        ) {
            CharacterRegistry.getDefaultCharacterId(context)
        } else {
            characterId
        }
    }
}
