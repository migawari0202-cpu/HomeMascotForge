package com.mascotforge.character

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Calendar

/**
 * キャラクターの状態を保持するデータクラス
 *
 * プロパティ:
 * - characterId: キャラクター識別子（例: "neko", "inu"）
 * - touchCount: 総タッチ回数（リセットされない）
 * - touchCountToday: 今日のタッチ回数（日付変更でリセット）
 * - lastTouchTime: 最後にタッチされた時刻（ミリ秒）
 * - customVars: Semantic-Free Variables（20個の整数配列）
 */
data class CharacterState(
    val characterId: String,
    var touchCount: Int = 0,
    var touchCountToday: Int = 0,
    var lastTouchTime: Long = 0L,
    val customVars: IntArray = IntArray(20) { 0 }  // Semantic-Free Variables
) {
    /**
     * 特定のカスタム変数を取得
     */
    fun getCustomVar(index: Int): Int {
        require(index in 0..19) { "Index must be 0-19, got $index" }
        return customVars[index]
    }

    /**
     * 特定のカスタム変数を設定
     */
    fun setCustomVar(index: Int, value: Int) {
        require(index in 0..19) { "Index must be 0-19, got $index" }
        require(value in 0..100) { "Value must be 0-100, got $value" }
        customVars[index] = value
    }

    /**
     * カスタム変数を調整（+, -, *, /, = 演算）
     */
    fun adjustCustomVar(index: Int, operation: String) {
        require(index in 0..19) { "Index must be 0-19, got $index" }

        val current = customVars[index]
        val newValue = when {
            operation.startsWith("+") -> current + operation.substring(1).toIntOrNull().orZero()
            operation.startsWith("-") -> current - operation.substring(1).toIntOrNull().orZero()
            operation.startsWith("*") -> current * operation.substring(1).toIntOrNull().orOne()
            operation.startsWith("/") -> {
                val divisor = operation.substring(1).toIntOrNull().orOne()
                if (divisor != 0) current / divisor else current
            }
            operation.startsWith("=") -> operation.substring(1).toIntOrNull().orZero()
            else -> current
        }.coerceIn(0, 100)  // 範囲制限

        customVars[index] = newValue
    }

    private fun String.toIntOrNull(): Int? = this.toIntOrNull()
    private fun Int?.orZero(): Int = this ?: 0
    private fun Int?.orOne(): Int = this ?: 1

    /**
     * 最後のタッチから経過した時間（秒）
     */
    fun getTimeSinceLastTouch(): Long {
        if (lastTouchTime == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - lastTouchTime) / 1000
    }

    /**
     * 今日タッチされたか
     */
    fun wasTouchedToday(): Boolean {
        return touchCountToday > 0
    }

    /**
     * デバッグ情報
     */
    override fun toString(): String {
        return "CharacterState(id=$characterId, touch=$touchCount, today=$touchCountToday, " +
                "lastTouch=${getTimeSinceLastTouch()}s ago, " +
                "customVars=[${customVars.take(5).joinToString()}...])"
    }

    /**
     * IntArray の equals/hashCode 対応
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacterState

        if (characterId != other.characterId) return false
        if (touchCount != other.touchCount) return false
        if (touchCountToday != other.touchCountToday) return false
        if (lastTouchTime != other.lastTouchTime) return false
        if (!customVars.contentEquals(other.customVars)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = characterId.hashCode()
        result = 31 * result + touchCount
        result = 31 * result + touchCountToday
        result = 31 * result + lastTouchTime.hashCode()
        result = 31 * result + customVars.contentHashCode()
        return result
    }
}

/**
 * キャラクター別の状態管理クラス
 *
 * 特徴:
 * - キャラクターIDごとに完全に独立した状態を管理
 * - SharedPreferences を使用した永続化
 * - スレッドセーフな実装
 * - 日付変更の自動検出とリセット
 * - Semantic-Free Variables のサポート
 *
 * キー形式: "{characterId}_{property}"
 * 例: "neko_touch_count", "inu_custom_var_0"
 */
class CharacterStateManager(private val context: Context) {

    companion object {
        private const val TAG = "CharacterStateManager"
        private const val PREFS_NAME = "character_states"

        // キーのプロパティ名
        private const val KEY_TOUCH_COUNT = "touch_count"
        private const val KEY_TOUCH_COUNT_TODAY = "touch_count_today"
        private const val KEY_LAST_TOUCH_TIME = "last_touch_time"
        private const val KEY_LAST_SAVE_DATE = "last_save_date"
        private const val KEY_CUSTOM_VAR_PREFIX = "custom_var"

        // デフォルト値
        private const val DEFAULT_TOUCH_COUNT = 0
        private const val DEFAULT_LAST_TOUCH_TIME = 0L
    }

    // SharedPreferences（遅延初期化）
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 公開メソッド
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * キャラクターIDから状態を取得
     *
     * @param characterId キャラクターID（例: "neko"）
     * @return キャラクターの現在の状態
     */
    fun getState(characterId: String): CharacterState {
        validateCharacterId(characterId)

        // 基本情報を取得
        val touchCount = prefs.getInt(makeKey(characterId, KEY_TOUCH_COUNT), DEFAULT_TOUCH_COUNT)
        val touchCountToday = prefs.getInt(makeKey(characterId, KEY_TOUCH_COUNT_TODAY), DEFAULT_TOUCH_COUNT)
        val lastTouchTime = prefs.getLong(makeKey(characterId, KEY_LAST_TOUCH_TIME), DEFAULT_LAST_TOUCH_TIME)
        val lastSaveDate = prefs.getString(makeKey(characterId, KEY_LAST_SAVE_DATE), null)

        // Semantic-Free Variables を取得
        val customVars = IntArray(20) { index ->
            prefs.getInt(makeKey(characterId, "$KEY_CUSTOM_VAR_PREFIX$index"), 0)
        }

        // 日付が変わっていたらリセット
        val today = getCurrentDate()
        val actualTouchCountToday = if (lastSaveDate == today) {
            touchCountToday
        } else {
            Log.d(TAG, "Date changed for $characterId: $lastSaveDate -> $today, resetting touchCountToday")
            0
        }

        val state = CharacterState(
            characterId = characterId,
            touchCount = touchCount,
            touchCountToday = actualTouchCountToday,
            lastTouchTime = lastTouchTime,
            customVars = customVars
        )

        Log.d(TAG, "State loaded: $state")
        return state
    }

    /**
     * タッチを記録（メモリ上で更新）
     *
     * @param state 更新対象の状態
     */
    fun recordTouch(state: CharacterState) {
        state.touchCount++
        state.touchCountToday++
        state.lastTouchTime = System.currentTimeMillis()

        Log.d(TAG, "Touch recorded: ${state.characterId} -> " +
                "total=${state.touchCount}, today=${state.touchCountToday}")
    }

    /**
     * 状態を保存（SharedPreferences に永続化）
     *
     * @param characterId キャラクターID
     * @param state 保存する状態
     */
    fun saveState(characterId: String, state: CharacterState) {
        validateCharacterId(characterId)
        require(state.characterId == characterId) {
            "CharacterID mismatch: expected $characterId, got ${state.characterId}"
        }

        prefs.edit().apply {
            // 基本情報を保存
            putInt(makeKey(characterId, KEY_TOUCH_COUNT), state.touchCount)
            putInt(makeKey(characterId, KEY_TOUCH_COUNT_TODAY), state.touchCountToday)
            putLong(makeKey(characterId, KEY_LAST_TOUCH_TIME), state.lastTouchTime)
            putString(makeKey(characterId, KEY_LAST_SAVE_DATE), getCurrentDate())

            // Semantic-Free Variables を保存
            state.customVars.forEachIndexed { index, value ->
                putInt(makeKey(characterId, "$KEY_CUSTOM_VAR_PREFIX$index"), value)
            }

            apply()
        }

        Log.d(TAG, "State saved: $characterId -> " +
                "touch=${state.touchCount}, today=${state.touchCountToday}")
    }

    /**
     * 特定のキャラクターの状態を削除
     *
     * @param characterId キャラクターID
     */
    fun deleteState(characterId: String) {
        validateCharacterId(characterId)

        prefs.edit().apply {
            remove(makeKey(characterId, KEY_TOUCH_COUNT))
            remove(makeKey(characterId, KEY_TOUCH_COUNT_TODAY))
            remove(makeKey(characterId, KEY_LAST_TOUCH_TIME))
            remove(makeKey(characterId, KEY_LAST_SAVE_DATE))

            for (i in 0 until 20) {
                remove(makeKey(characterId, "$KEY_CUSTOM_VAR_PREFIX$i"))
            }

            apply()
        }

        Log.d(TAG, "State deleted: $characterId")
    }

    /**
     * すべての状態を削除（危険な操作）
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.w(TAG, "All character states cleared")
    }

    /**
     * すべてのキャラクターIDを取得
     *
     * @return キャラクターIDのリスト
     */
    fun getAllCharacterIds(): List<String> {
        val ids = mutableSetOf<String>()

        prefs.all.keys.forEach { key ->
            // "neko_touch_count" から "neko" を抽出
            val parts = key.split("_", limit = 2)
            if (parts.size >= 2) {
                ids.add(parts[0])
            }
        }

        return ids.toList().sorted()
    }

    /**
     * デバッグ情報を取得
     */
    fun getDebugInfo(): String {
        val allIds = getAllCharacterIds()
        return buildString {
            appendLine("CharacterStateManager Debug Info:")
            appendLine("  Total characters: ${allIds.size}")
            appendLine("  Characters: ${allIds.joinToString()}")
            appendLine("  Storage keys: ${prefs.all.size}")
            appendLine()

            allIds.forEach { characterId ->
                val state = getState(characterId)
                appendLine("  $characterId:")
                appendLine("    touchCount: ${state.touchCount}")
                appendLine("    touchCountToday: ${state.touchCountToday}")
                appendLine("    lastTouchTime: ${state.lastTouchTime}")
                appendLine("    timeSinceLastTouch: ${state.getTimeSinceLastTouch()}s")
                appendLine("    customVars[0-4]: ${state.customVars.take(5).joinToString()}")
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // プライベートメソッド
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * キーを生成（キャラID + プロパティ名）
     *
     * @param characterId キャラクターID
     * @param property プロパティ名
     * @return 生成されたキー（例: "neko_touch_count"）
     */
    private fun makeKey(characterId: String, property: String): String {
        return "${characterId}_${property}"
    }

    /**
     * 現在の日付を取得（YYYY-MM-DD形式）
     */
    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * キャラクターIDのバリデーション
     */
    private fun validateCharacterId(characterId: String) {
        require(characterId.isNotBlank()) { "CharacterID cannot be blank" }
        require(characterId.matches(Regex("^[a-zA-Z0-9_-]{1,50}$"))) {
            "Invalid characterID: $characterId. Must match ^[a-zA-Z0-9_-]{1,50}$"
        }
    }
}