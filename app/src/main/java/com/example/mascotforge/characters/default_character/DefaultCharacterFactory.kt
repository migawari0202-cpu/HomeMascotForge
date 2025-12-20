package com.example.mascotforge.characters.default_character

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.mascotforge.CharacterFactory
import com.example.mascotforge.CharacterProvider

/**
 * デフォルトキャラクターのFactory
 *
 * 「DefaultCharacter」を生成し、アプリの基本キャラとして登録される。
 */
class DefaultCharacterFactory : CharacterFactory {

    override fun getCharacterId(): String = "default_character"

    override fun create(context: Context): CharacterProvider {
        return DefaultCharacter(context)
    }

    override fun getThumbnail(context: Context): Drawable? {
        return try {
            context.assets.open("characters/default_character/images/thumbnail.png").use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                BitmapDrawable(context.resources, bitmap)
            }
        } catch (e: Exception) {
            // 万一 thumbnail.png が存在しない場合のフォールバック
            null
        }
    }

    override fun getDisplayName(context: Context): String {
        return "デフォルトキャラクター"
    }

    override fun getDescription(context: Context): String {
        return "天気・時間・祝日当の条件に応じてセリフや表情が変化する初期キャラクターです。"
    }

    override fun getAuthor(context: Context): String {
        return "開発した人"
    }

    override fun getVersion(): String {
        return "1.0.0"
    }
}
