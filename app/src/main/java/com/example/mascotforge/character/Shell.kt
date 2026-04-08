package com.example.mascotforge.character

/**
 * Shellは伺かのShell相当。
 * キャラクターのロジック（セリフ・感情ルール）はそのままに、
 * 見た目（画像）だけを差し替えるパッケージ。
 */
data class Shell(
    val id: String,
    val name: String,

    /**
     * このShellが対象とするキャラクターのID。
     * 不一致の場合でも動作するが、ShellRegistryが警告を出す。
     */
    val targetCharacterId: String,

    /**
     * 感情名 → 画像ファイル名 のマッピング。
     * 例: "happy" → "winter_happy.webp"
     *
     * 画像解決の優先順位:
     *   1. このemotionMapping[emotion]
     *   2. キャラクター本来のimageMapping[emotion]
     */
    val emotionMapping: Map<String, String>,

    /** R18コンテンツフラグ。trueのとき適用時に警告が出る。 */
    val isR18: Boolean = false
)
