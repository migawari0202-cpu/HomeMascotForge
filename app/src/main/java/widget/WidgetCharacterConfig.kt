package com.example.mascotforge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * ウィジェットごとのキャラクター設定を管理
 *
 * 各ウィジェットが独立したキャラクターを持てるようにする
 */
class WidgetCharacterConfig(private val context: Context) {

    companion object {
        private const val TAG = "WidgetCharacterConfig"
        private const val PREFS_NAME = "widget_character_config"
        private const val KEY_PREFIX_CHARACTER = "widget_char_"
        private const val KEY_DEFAULT_CHARACTER = "default_character_id"

        // デフォルトキャラID
        private const val FALLBACK_CHARACTER_ID = "default_character"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 特定ウィジェットのキャラクターIDを取得
     */
    fun getCharacterForWidget(widgetId: Int): String {
        val key = "$KEY_PREFIX_CHARACTER$widgetId"
        val characterId = prefs.getString(key, null)

        if (characterId != null) {
            Log.d(TAG, "Widget #$widgetId uses: $characterId")
            return characterId
        }

        // ウィジェット固有の設定がない場合、デフォルトを使用
        val defaultCharId = getDefaultCharacter()
        Log.d(TAG, "Widget #$widgetId uses default: $defaultCharId")
        return defaultCharId
    }

    /**
     * 特定ウィジェットにキャラクターを設定
     */
    fun setCharacterForWidget(widgetId: Int, characterId: String) {
        val key = "$KEY_PREFIX_CHARACTER$widgetId"
        prefs.edit().putString(key, characterId).apply()
        Log.i(TAG, "Widget #$widgetId → $characterId")
    }

    /**
     * デフォルトキャラクターIDを取得
     */
    fun getDefaultCharacter(): String {
        return prefs.getString(KEY_DEFAULT_CHARACTER, FALLBACK_CHARACTER_ID)
            ?: FALLBACK_CHARACTER_ID
    }

    /**
     * デフォルトキャラクターIDを設定
     */
    fun setDefaultCharacter(characterId: String) {
        prefs.edit().putString(KEY_DEFAULT_CHARACTER, characterId).apply()
        Log.i(TAG, "Default character → $characterId")
    }

    /**
     * ウィジェット削除時に設定をクリア
     */
    fun clearWidgetConfig(widgetId: Int) {
        val key = "$KEY_PREFIX_CHARACTER$widgetId"
        prefs.edit().remove(key).apply()
        Log.i(TAG, "Widget #$widgetId config cleared")
    }

    /**
     * 全ウィジェットの設定を取得（デバッグ用）
     */
    fun getAllWidgetConfigs(): Map<Int, String> {
        val configs = mutableMapOf<Int, String>()

        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX_CHARACTER) && value is String) {
                val widgetId = key.removePrefix(KEY_PREFIX_CHARACTER).toIntOrNull()
                if (widgetId != null) {
                    configs[widgetId] = value
                }
            }
        }

        return configs
    }

    /**
     * 指定されたキャラクターを使用しているウィジェット一覧
     */
    fun getWidgetsUsingCharacter(characterId: String): List<Int> {
        return getAllWidgetConfigs()
            .filterValues { it == characterId }
            .keys
            .toList()
    }
}