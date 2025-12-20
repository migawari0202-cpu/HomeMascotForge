package com.mascotforge.character.speech

import android.util.Log

/**
 * セリフ内のタグを解析するクラス
 *
 * 対応タグ:
 * - [emotion:xxx] - 感情指定
 * - [var: xxx] - 変数操作
 * - {br} - 改行
 */
class TagParser {

    companion object {
        private const val TAG = "TagParser"

        // 正規表現パターン
        private val EMOTION_REGEX = "\\[emotion:([a-zA-Z0-9_]{1,30})\\]".toRegex()
        private val VAR_REGEX = "\\[var:\\s*([^\\]]+)\\]".toRegex()
        private const val BR_TAG = "{br}"
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
                Log.d(TAG, "Parsed variable operation: ${op.type} ${op.varName} = ${op.value}")
            }
        }
        workingText = VAR_REGEX.replace(workingText, "")

        // 3. {br} タグを実際の改行に変換
        workingText = workingText.replace(BR_TAG, "\n")

        // 4. 余分な空白をトリム
        val cleanedText = workingText.trim()

        return ParsedSpeech(
            cleanedText = cleanedText,
            emotion = emotion,
            variableOperations = variableOps
        )
    }

    /**
     * [var: xxx + 5] や [var: isHungry = true] をパース
     *
     * サポート形式:
     * - [var: varName + 数値]  → 加算
     * - [var: varName - 数値]  → 減算
     * - [var: varName = 値]    → 代入
     */
    private fun parseVariableOperation(expression: String): VariableOperation? {
        return try {
            when {
                // 加算: friendship + 5
                "+" in expression -> {
                    val parts = expression.split("+").map { it.trim() }
                    if (parts.size != 2) return null
                    val varName = parts[0]
                    val value = parts[1].toIntOrNull() ?: return null
                    VariableOperation(OperationType.ADD, varName, value)
                }

                // 減算: hungerLevel - 2
                "-" in expression -> {
                    val parts = expression.split("-").map { it.trim() }
                    if (parts.size != 2) return null
                    val varName = parts[0]
                    val value = parts[1].toIntOrNull() ?: return null
                    VariableOperation(OperationType.SUBTRACT, varName, value)
                }

                // 代入: isHungry = true
                "=" in expression -> {
                    val parts = expression.split("=").map { it.trim() }
                    if (parts.size != 2) return null
                    val varName = parts[0]
                    val valueStr = parts[1]

                    // Boolean変換を試す
                    val value: Any = when (valueStr.lowercase()) {
                        "true" -> true
                        "false" -> false
                        else -> valueStr.toIntOrNull() ?: valueStr // 数値または文字列
                    }

                    VariableOperation(OperationType.SET, varName, value)
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse variable operation: $expression", e)
            null
        }
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
 * 変数操作の指示
 */
data class VariableOperation(
    val type: OperationType,
    val varName: String,
    val value: Any
)

/**
 * 変数操作の種類
 */
enum class OperationType {
    ADD,      // +
    SUBTRACT, // -
    SET       // =
}