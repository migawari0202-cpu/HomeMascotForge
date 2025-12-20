package com.example.mascotforge

import android.content.Context
import com.example.mascotforge.characters.CharacterRegistry

/**
 * キャラクター管理クラス
 */
class CharacterManager(private val context: Context) {

    /**
     * 現在選択されているキャラクタープロバイダーを取得
     * @deprecated ウィジェットIDを指定する getProviderForWidget を使用してください
     */
    fun getCurrentProvider(): CharacterProvider {
        val selectedId = CharacterPreferences.getSelectedCharacterId(context)
        val allProviders = getAllProviders()
        return allProviders.find { it.id == selectedId } ?: allProviders.first()
    }

    /**
     * 特定ウィジェット用のキャラクタープロバイダーを取得
     */
    fun getProviderForWidget(widgetId: Int): CharacterProvider {
        val selectedId = CharacterPreferences.getCharacterIdForWidget(context, widgetId)
        val allProviders = getAllProviders()
        return allProviders.find { it.id == selectedId } ?: allProviders.first()
    }

    /**
     * 利用可能な全キャラクタープロバイダーを取得
     */
    fun getAllProviders(): List<CharacterProvider> {
        return CharacterRegistry.getInternalCharacters(context)
    }

    /**
     * 現在選択されているキャラのIDを取得
     * @deprecated ウィジェットIDを指定する getCharacterIdForWidget を使用してください
     */
    fun getCurrentCharacterId(): String {
        return CharacterPreferences.getSelectedCharacterId(context)
    }

    /**
     * 特定ウィジェットのキャラIDを取得
     */
    fun getCharacterIdForWidget(widgetId: Int): String {
        return CharacterPreferences.getCharacterIdForWidget(context, widgetId)
    }
}
