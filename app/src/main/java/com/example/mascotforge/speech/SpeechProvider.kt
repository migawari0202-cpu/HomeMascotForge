package com.mascotforge.speech

import android.graphics.Bitmap

interface SpeechProvider {
    suspend fun getSpeech(context: SpeechContext): String?

    // キャラ側で感情を決める形で安定
    fun getCharaImage(context: SpeechContext): Bitmap?
}
