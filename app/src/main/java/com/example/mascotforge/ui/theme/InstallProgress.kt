package com.mascotforge.ui

// インストール進行状況を表すデータクラス
data class InstallProgress(
    val phase: InstallPhase,
    val progress: Float = 0f,   // 0.0〜1.0
    val message: String = ""    // 現在の進行状況メッセージ
)
