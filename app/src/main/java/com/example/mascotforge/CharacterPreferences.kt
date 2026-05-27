

package com.example.mascotforge

import android.content.Context
import android.content.SharedPreferences
import com.example.mascotforge.characters.CharacterRegistry

object CharacterPreferences {
    private const val PREF_NAME = "character_settings"
    private const val KEY_SELECTED_CHARACTER = "selected_character_id"
    private const val KEY_WIDGET_CHARACTER_PREFIX = "widget_character_"
    private const val REMOVED_DEFAULT_CHARACTER_ID = "default"
    private const val LEGACY_DEFAULT_CHARACTER_ID = "default_character"
    private const val REMOVED_EVIL_CHARACTER_ID = "evil"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedCharacterId(context: Context): String {
        val prefs = getPrefs(context)
        val fallbackId = CharacterRegistry.getDefaultCharacterId(context)
        val characterId = prefs.getString(KEY_SELECTED_CHARACTER, fallbackId) ?: fallbackId
        val normalized = normalizeCharacterId(context, characterId)
        if (normalized != characterId) {
            setSelectedCharacterId(context, normalized)
        }
        return normalized
    }

    fun setSelectedCharacterId(context: Context, characterId: String) {
        getPrefs(context).edit()
            .putString(KEY_SELECTED_CHARACTER, characterId)
            .apply()
    }

    fun getCharacterIdForWidget(context: Context, widgetId: Int): String {
        val prefs = getPrefs(context)
        val characterId = prefs.getString(
            "$KEY_WIDGET_CHARACTER_PREFIX$widgetId",
            null
        ) ?: getSelectedCharacterId(context)
        val normalized = normalizeCharacterId(context, characterId)
        if (normalized != characterId) {
            setCharacterIdForWidget(context, widgetId, normalized)
        }
        return normalized
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

    private fun normalizeCharacterId(context: Context, characterId: String): String {
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
