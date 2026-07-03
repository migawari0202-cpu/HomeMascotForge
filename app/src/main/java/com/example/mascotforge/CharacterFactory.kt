package com.example.mascotforge

import android.content.Context
import android.graphics.drawable.Drawable

/**
 * 内蔵キャラクターのFactory用インターフェース
 *
 * 各キャラクターFactoryはこれを実装する
 */
interface CharacterFactory {

    /** キャラクターの一意なID */
    fun getCharacterId(): String

    /** CharacterProvider を生成 */
    fun create(context: Context): CharacterProvider

    /**
     * サムネイル画像の取得
     * @return Drawable または null
     */
    fun getThumbnail(context: Context): Drawable? = null

    /**
     * サムネイル画像のリソースIDを取得(オプション)
     * getThumbnail()の代わりにこちらを実装してもよい
     */
    fun getThumbnailResId(): Int? = null

    /** キャラクターの表示名 */
    fun getDisplayName(context: Context): String

    /** キャラクターの説明 */
    fun getDescription(context: Context): String = ""

    /** キャラクターの作者 */
    fun getAuthor(context: Context): String = ""

    /** キャラクターのバージョン(オプション) */
    fun getVersion(): String = "1.0.0"
}