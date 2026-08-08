
package com.example.mascotforge

import android.content.Context
import android.content.SharedPreferences
import com.example.mascotforge.characters.CharacterRegistry

/**
 * キャラクター選択の永続化。
 *
 * MVP 方針: Widget / Activity 共通で [selected_character_id] のみを使う。
 * 旧 per-widget キー API は互換のため残すが、中身はグローバル選択に委譲する。
 */
object CharacterPreferences {
    private const val PREF_NAME = "character_settings"
    private const val KEY_SELECTED_CHARACTER = "selected_character_id"
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

    /**
     * MVP: Widget/Activity 共通。widgetId は無視しグローバル選択を返す。
     */
    fun getCharacterIdForWidget(context: Context, widgetId: Int): String {
        return getSelectedCharacterId(context)
    }

    /**
     * MVP: Widget/Activity 共通。グローバル選択へ書き込む。
     */
    fun setCharacterIdForWidget(context: Context, widgetId: Int, characterId: String) {
        setSelectedCharacterId(context, characterId)
    }

    /**
     * MVP: Widget/Activity 共通。グローバル選択へ書き込む。
     */
    fun setCharacterIdForWidgets(context: Context, widgetIds: List<Int>, characterId: String) {
        setSelectedCharacterId(context, characterId)
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
