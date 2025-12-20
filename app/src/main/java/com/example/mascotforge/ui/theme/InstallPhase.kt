package com.mascotforge.ui

// インストールの各段階を表す列挙型
enum class InstallPhase {
    READING,     // ファイル読み込み
    EXTRACTING,  // 解凍中
    VALIDATING,  // 検証中
    INSTALLING,  // インストール中
    COMPLETED    // 完了
}
