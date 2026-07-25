package com.example.mascotforge

import android.content.Context
import com.example.mascotforge.characters.CharacterRegistry

/**
 * キャラクター管理クラス。
 *
 * MVP: Widget / Activity ともグローバル選択（[CharacterPreferences.getSelectedCharacterId]）を使う。
 * widgetId 付き API は呼び出し互換のため残し、内部は共通選択に委譲する。
 */
class CharacterManager(private val context: Context) {

    /**
     * 現在選択されているキャラクタープロバイダーを取得（Widget/Activity 共通）
     */
    fun getCurrentProvider(): CharacterProvider {
        val selectedId = CharacterPreferences.getSelectedCharacterId(context)
        val allProviders = getAllProviders()
        return allProviders.find { it.id == selectedId }
            ?: allProviders.firstOrNull()
            ?: error("No characters available")
    }

    /**
     * MVP: widgetId は無視し [getCurrentProvider] と同じ。
     */
    fun getProviderForWidget(widgetId: Int): CharacterProvider {
        return getCurrentProvider()
    }

    /**
     * 利用可能な全キャラクタープロバイダーを取得
     */
    fun getAllProviders(): List<CharacterProvider> {
        return CharacterRegistry.getInternalCharacters(context)
    }

    /**
     * 現在選択されているキャラのIDを取得（Widget/Activity 共通）
     */
    fun getCurrentCharacterId(): String {
        return CharacterPreferences.getSelectedCharacterId(context)
    }

    /**
     * MVP: widgetId は無視し [getCurrentCharacterId] と同じ。
     */
    fun getCharacterIdForWidget(widgetId: Int): String {
        return getCurrentCharacterId()
    }
}
