/**
 * 変数操作ルール
 */
data class VariableRule(
    val trigger: String,                    // "onDailyMeet", "onWidgetTap", etc.
    val variable: String,                   // "custom0", "custom1", etc.
    val operation: String,                  // "add", "set", "multiply", etc.
    val value: Int,                         // 操作する値
    val condition: Map<String, String>? = null  // 条件（オプション）
)

/**
 * 変数管理設定
 */
data class VariableConfig(
    val version: String,
    val documentation: Map<String, String>,  // 変数の意味（エンジンは無視）
    val rules: List<VariableRule>
)

/**
 * キャラクターの状態
 */
data class CharacterState(
    val characterId: String,

    // 自動生成変数（読み取り専用）
    val dailyVars: List<Double>,     // daily0-9
    val hourlyVars: List<Double>,    // hourly0-9
    val weeklyVars: List<Double>,    // weekly0-9

    // カスタム変数（読み書き可能）
    val customVars: MutableList<Int>, // custom0-19

    // メタ情報
    val lastUpdate: Long,
    val lastDailyUpdate: Long,
    val lastHourlyUpdate: Long,
    val lastWeeklyUpdate: Long
)