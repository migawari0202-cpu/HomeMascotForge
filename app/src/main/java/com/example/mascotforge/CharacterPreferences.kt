package com.example.mascotforge

import android.content.Context
import android.content.SharedPreferences
import com.example.mascotforge.characters.CharacterRegistry

object CharacterPreferences {
    private const val PREF_NAME = "character_settings"
    private const val KEY_SELECTED_CHARACTER = "selected_character_id"
    private const val KEY_WIDGET_CHARACTER_PREFIX = "widget_character_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * デフォルトの選択中キャラIDを取得（後方互換性のため残す）
     */
    fun getSelectedCharacterId(context: Context): String {
        return getPrefs(context).getString(
            KEY_SELECTED_CHARACTER,
            CharacterRegistry.getDefaultCharacterId()
        ) ?: CharacterRegistry.getDefaultCharacterId()
    }

    /**
     * デフォルトのキャラを選択（後方互換性のため残す）
     */
    fun setSelectedCharacterId(context: Context, characterId: String) {
        getPrefs(context).edit()
            .putString(KEY_SELECTED_CHARACTER, characterId)
            .apply()
    }

    /**
     * 特定ウィジェットのキャラIDを取得
     */
    fun getCharacterIdForWidget(context: Context, widgetId: Int): String {
        val prefs = getPrefs(context)
        return prefs.getString(
            "$KEY_WIDGET_CHARACTER_PREFIX$widgetId",
            null
        ) ?: getSelectedCharacterId(context) // フォールバック：デフォルトキャラを返す
    }

    /**
     * 特定ウィジェットにキャラを設定
     */
    fun setCharacterIdForWidget(context: Context, widgetId: Int, characterId: String) {
        getPrefs(context).edit()
            .putString("$KEY_WIDGET_CHARACTER_PREFIX$widgetId", characterId)
            .apply()
    }

    /**
     * 複数ウィジェットに一括設定
     */
    fun setCharacterIdForWidgets(context: Context, widgetIds: List<Int>, characterId: String) {
        val editor = getPrefs(context).edit()
        widgetIds.forEach { widgetId ->
            editor.putString("$KEY_WIDGET_CHARACTER_PREFIX$widgetId", characterId)
        }
        editor.apply()
    }
}