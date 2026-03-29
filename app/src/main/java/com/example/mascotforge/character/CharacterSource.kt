package com.mascotforge.character

/**
 * キャラクターファイルの読み込み元を表す sealed class。
 *
 * basePath + isAssets の二引数ペアをまとめることで、
 * メソッドシグネチャのノイズを排除する。
 */
sealed class CharacterSource {
    abstract val basePath: String

    /** APK同梱キャラ（assets/以下） */
    data class Assets(override val basePath: String) : CharacterSource()

    /** ZIPインストールキャラ（filesDir/以下） */
    data class InstalledFiles(override val basePath: String) : CharacterSource()
}
