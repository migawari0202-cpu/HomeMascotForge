package com.example.mascotforge.character.speech

import android.util.Log
import com.example.mascotforge.character.CharacterState
import com.example.mascotforge.character.CharacterStateManager

/**
 * セリフ内のタグを解析するクラス（完全統合版）
 *
 * 対応タグ:
 * - [emotion:xxx] / [e:xxx] - 感情指定
 * - [v: xxx] - 変数操作（変数名 or customVars[n]）
 * - {br} - 改行
 *
 * 統合の特徴:
 * - DynamicCharacter用: 変数名ベース（例: favorability + 5）
 * - IntegrationExample用: インデックスベース（例: customVars[0] + 5）
 * - CharacterState連携で自動保存
 */
class TagParser(
    private val characterState: CharacterState? = null,
    private val stateManager: CharacterStateManager? = null,
    private val characterId: String? = null
) {

    companion object {
        private const val TAG = "TagParser"

        // 正規表現パターン
        private val EMOTION_REGEX = "\\[(?:emotion|e):([a-zA-Z0-9_]{1,30})\\]".toRegex()
        private val VAR_REGEX = "\\[(?:v|var):\\s*([^\\]]+)\\]".toRegex()
        private val LITERAL_NEWLINE_REGEX = "\\\\n".toRegex()
    }

    /**
     * セリフを解析してタグ情報を抽出
     *
     * @param speech 元のセリフテキスト
     * @return 解析結果（ParsedSpeech）
     */
    fun parse(speech: String): ParsedSpeech {
        var workingText = speech
        var emotion: String? = null
        val variableOps = mutableListOf<VariableOperation>()

        // 1. [emotion:xxx] タグを抽出（最初の1つのみ有効）
        val emotionMatch = EMOTION_REGEX.find(workingText)
        if (emotionMatch != null) {
            emotion = emotionMatch.groupValues[1]
            workingText = EMOTION_REGEX.replace(workingText, "")
            Log.d(TAG, "Extracted emotion: $emotion")
        }

        // 2. [var: ...] タグを全て抽出
        VAR_REGEX.findAll(workingText).forEach { match ->
            val expression = match.groupValues[1].trim()
            parseVariableOperation(expression)?.let { op ->
                variableOps.add(op)
                Log.d(TAG, "Parsed variable operation: ${op.type} ${op.varName} ${op.operationValue}${op.stringValue?.let { " \"$it\"" } ?: ""}")
            }
        }
        workingText = VAR_REGEX.replace(workingText, "")

        // 3. {br} タグ・\n を実際の改行に変換
        workingText = workingText.replace("{br}", "\n", ignoreCase = true)
        workingText = LITERAL_NEWLINE_REGEX.replace(workingText, "\n")

        // 4. 余分な空白をトリム
        val cleanedText = workingText.trim()

        return ParsedSpeech(
            cleanedText = cleanedText,
            emotion = emotion,
            variableOperations = variableOps
        )
    }

    /**
     * 変数操作式をパース（変数名ベースのみ対応）
     *
     * サポート形式:
     * - [var: favorability + 5]
     * - [var: trust = 100]
     */
    private fun parseVariableOperation(expression: String): VariableOperation? {
        return try {
            parseNameBasedOperation(expression)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse variable operation: $expression", e)
            null
        }
    }

    /**
     * 変数名ベースの変数操作をパース
     * 例: favorability + 5
     */
    private fun parseNameBasedOperation(expression: String): VariableOperation? {
        // 変数名を抽出（英数字とアンダースコアのみ）
        val varPattern = Regex("([a-zA-Z_][a-zA-Z0-9_]*)")
        val varMatch = varPattern.find(expression) ?: return null
        val variableName = varMatch.value

        // 演算子を検出
        val afterVar = expression.substring(varMatch.range.last + 1).trim()

        return when {
            afterVar.startsWith("+") -> {
                val value = afterVar.substring(1).trim().toIntOrNull() ?: return null
                VariableOperation(OperationType.ADD, variableName, value)
            }
            afterVar.startsWith("-") -> {
                val value = afterVar.substring(1).trim().toIntOrNull() ?: return null
                VariableOperation(OperationType.SUBTRACT, variableName, value)
            }
            afterVar.startsWith("*") -> {
                val value = afterVar.substring(1).trim().toIntOrNull() ?: return null
                VariableOperation(OperationType.MULTIPLY, variableName, value)
            }
            afterVar.startsWith("/") -> {
                val value = afterVar.substring(1).trim().toIntOrNull() ?: return null
                if (value == 0) { Log.w(TAG, "Division by zero"); return null }
                VariableOperation(OperationType.DIVIDE, variableName, value)
            }
            afterVar.startsWith("=") -> {
                val rhs = afterVar.substring(1).trim()

                // 1. 整数 → 数値SET（既存動作を維持）
                val intVal = rhs.toIntOrNull()
                if (intVal != null) {
                    return VariableOperation(OperationType.SET, variableName, intVal)
                }

                // 2. toggle キーワード → boolean反転
                if (rhs.equals("toggle", ignoreCase = true)) {
                    return VariableOperation(OperationType.TOGGLE, variableName, stringValue = "toggle")
                }

                // 3. boolean リテラル (true/false)
                if (rhs.equals("true", ignoreCase = true) || rhs.equals("false", ignoreCase = true)) {
                    return VariableOperation(OperationType.SET_STRING, variableName, stringValue = rhs.lowercase())
                }

                // 4. 文字列値（英数字＋アンダースコア、最大50文字）
                if (rhs.length in 1..50 && rhs.matches(Regex("[a-zA-Z0-9_]+"))) {
                    return VariableOperation(OperationType.SET_STRING, variableName, stringValue = rhs)
                }

                Log.w(TAG, "Unrecognised SET value in: $expression")
                null
            }
            else -> {
                Log.w(TAG, "Unknown operation in: $expression")
                null
            }
        }
    }

    /**
     * パース結果を実際にCharacterStateに適用する（自動保存版）
     *
     * @param operations 変数操作のリスト
     * @param autoSave 自動保存するかどうか（デフォルト: true）
     * @return 適用に成功したかどうか
     */
    fun applyOperations(operations: List<VariableOperation>, autoSave: Boolean = true): Boolean {
        if (characterState == null) {
            Log.w(TAG, "CharacterState is not provided, cannot apply operations")
            return false
        }

        if (operations.isEmpty()) {
            Log.d(TAG, "No operations to apply")
            return true
        }

        try {
            // 変数操作はすべて名前ベース。型変換・範囲制限は CustomVariableManager が担当するため
            // ここではログのみ記録し、DynamicCharacter 側で適用する。
            operations.forEach { op ->
                Log.d(TAG, "Variable operation pending (handled by CustomVariableManager): ${op.varName} ${op.type} ${op.operationValue}")
            }

            // 🔥 自動保存（インデックスベースの場合のみ）
            if (autoSave && stateManager != null && characterId != null) {
                stateManager.saveState(characterId, characterState)
                Log.d(TAG, "✅ Auto-saved state for character: $characterId")
            } else if (autoSave) {
                Log.w(TAG, "⚠️ Cannot auto-save: stateManager or characterId is null")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply operations", e)
            return false
        }
    }

    /**
     * 🔥 セリフを解析して変数操作を適用し、クリーンなテキストを返す（ワンライナー版）
     *
     * @param speech 元のセリフテキスト
     * @param autoSave 自動保存するかどうか（デフォルト: true）
     * @return 解析結果（変数操作は既に適用済み）
     */
    fun parseAndApply(speech: String, autoSave: Boolean = true): ParsedSpeech {
        val parsed = parse(speech)
        applyOperations(parsed.variableOperations, autoSave)
        return parsed
    }
}

/**
 * タグ解析結果
 */
data class ParsedSpeech(
    val cleanedText: String,                    // タグ除去後のテキスト（改行適用済み）
    val emotion: String?,                       // 感情キー
    val variableOperations: List<VariableOperation>  // 変数操作リスト
)

/**
 * 変数操作の指示（変数名ベース）
 */
data class VariableOperation(
    val type: OperationType,
    val varName: String,              // 変数名 (例: "favorability")
    val operationValue: Int = 0,      // 数値操作用 (ADD/SUBTRACT/MULTIPLY/DIVIDE/SET)
    val stringValue: String? = null   // 文字列・真偽値・toggle用 (SET_STRING/TOGGLE)
)

/**
 * 変数操作の種類
 */
enum class OperationType {
    ADD,        // +
    SUBTRACT,   // -
    MULTIPLY,   // *
    DIVIDE,     // /
    SET,        // = (数値)
    SET_STRING, // = (文字列 or 真偽値リテラル)
    TOGGLE      // =toggle (boolean反転)
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 便利な拡張関数
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * CharacterStateManagerの拡張関数: TagParserを作成
 */
fun CharacterStateManager.createParser(characterId: String): TagParser {
    val state = this.getState(characterId)
    return TagParser(state, this, characterId)
}

/**
 * 使用例:
 *
 * // IntegrationExample用
 * val parser = stateManager.createParser("neko")
 * val result = parser.parseAndApply("[var: customVars[0] + 5]")  // 自動保存
 *
 * // DynamicCharacter用
 * val parser = TagParser()
 * val result = parser.parse("[var: favorability + 5]")
 * // 変数操作はCustomVariableManagerで処理
 */