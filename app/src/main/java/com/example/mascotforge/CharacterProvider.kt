package com.example.mascotforge

import android.graphics.Bitmap
import com.example.mascotforge.speech.SpeechProvider
import com.example.mascotforge.speech.SpeechContext

/**
 * エンジン内部でキャラクターを統一的に扱うためのインターフェース
 * 内蔵キャラ(assets)と外部キャラ(ZIP)の両方に対応
 *
 * 【設計方針】
 * - キャラクターが状況を見て自分で感情・画像・セリフを決定
 * - エンジン側は状況情報を渡すだけで、判断には関与しない
 */
interface CharacterProvider {
    /** キャラクターの一意なID */
    val id: String

    /** キャラクターの表示名 */
    val name: String

    /**
     * 現在の状況に応じたセリフを取得
     * @param context エンジンが渡す状況情報
     * @return セリフ(取得できない場合はnull)
     */
    suspend fun getSpeech(context: SpeechContext): String?

    /**
     * 現在の状況に応じたキャラ画像を取得
     *
     * 【重要な変更点】
     * - 引数を String (emotion) から SpeechContext に変更
     * - キャラクター側が状況を見て自分で感情と画像を決定
     * - エンジン側は感情判断に関与しない
     *
     * @param context エンジンが渡す状況情報(天気、時間帯、バッテリーなど)
     * @return キャラクター画像(取得できない場合はnull)
     */
    fun getCharaImage(context: SpeechContext): Bitmap?

    /**
     * このプロバイダーが現在利用可能かチェック
     * 外部キャラの場合、ZIPから正しくインストールされているか確認
     */
    fun isAvailable(): Boolean = true
}

/**
 * 内蔵のSpeechProviderをCharacterProviderに変換するアダプター
 * デフォルトキャラなど、エンジンに同梱されたキャラ用(assets読み込み)
 */
class InternalCharacterAdapter(
    private val speechProvider: SpeechProvider,
    override val id: String,
    override val name: String
) : CharacterProvider {

    override suspend fun getSpeech(context: SpeechContext): String? {
        return speechProvider.getSpeech(context)
    }

    override fun getCharaImage(context: SpeechContext): Bitmap? {
        // SpeechProviderにcontextを渡す
        // SpeechProvider側も getCharaImage(context: SpeechContext) に変更が必要
        return speechProvider.getCharaImage(context)
    }

    override fun isAvailable(): Boolean = true
}