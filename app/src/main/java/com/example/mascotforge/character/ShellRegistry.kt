package com.example.mascotforge.character

import android.content.Context
import android.util.Log
import com.example.mascotforge.installer.ZipSecurityValidator
import org.json.JSONObject
import java.io.File

/**
 * キャラクターごとのアクティブShellを管理するレジストリ。
 *
 * 永続化: SharedPreferences "prefs_shells"
 *   キー: "active_shell_{characterId}" → shellId
 *
 * Shell画像は filesDir/shells/{shellId}/images/ に保存される。
 */
object ShellRegistry {

    private const val TAG = "ShellRegistry"
    private const val PREFS_NAME = "prefs_shells"
    private const val KEY_PREFIX = "active_shell_"

    /**
     * キャラクターのアクティブShellを取得する。
     * Shell未設定、またはShellファイルが見つからない場合はnullを返す。
     *
     * targetCharacterId が characterId と不一致の場合は警告を出すが処理を続行する。
     */
    fun getActiveShell(context: Context, characterId: String): Shell? {
        val shellId = getActiveShellId(context, characterId) ?: return null
        val shell = loadShell(context, shellId) ?: run {
            Log.w(TAG, "Active shell '$shellId' for '$characterId' could not be loaded. Clearing.")
            clearActiveShell(context, characterId)
            return null
        }

        if (shell.targetCharacterId.isNotEmpty() && shell.targetCharacterId != characterId) {
            Log.w(TAG, "Shell '${shell.id}' targets '${shell.targetCharacterId}' " +
                    "but applied to '$characterId'. Proceeding if filenames match.")
        }

        return shell
    }

    /**
     * キャラクターにShellを設定する。
     * R18フラグがtrueのShellを設定する場合、呼び出し元でユーザーへの警告表示を行うこと。
     */
    fun setActiveShell(context: Context, characterId: String, shellId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_PREFIX$characterId", shellId)
            .apply()
        Log.i(TAG, "Shell '$shellId' set for character '$characterId'")
    }

    /** キャラクターのアクティブShellを解除し、本来の画像を使用するようにする。 */
    fun clearActiveShell(context: Context, characterId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PREFIX$characterId")
            .apply()
        Log.i(TAG, "Shell cleared for character '$characterId'")
    }

    /** インストール済みShellのID一覧を返す。 */
    fun getInstalledShellIds(context: Context): List<String> {
        val shellsDir = File(context.filesDir, "shells")
        if (!shellsDir.exists()) return emptyList()
        return shellsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    /**
     * 指定IDのShellをファイルシステムから読み込む。
     * shell.jsonが見つからない、またはパースに失敗した場合はnullを返す。
     */
    fun loadShell(context: Context, shellId: String): Shell? {
        if (!ZipSecurityValidator.isValidCharacterId(shellId)) {
            Log.e(TAG, "Invalid shell ID format, refusing to load: $shellId")
            return null
        }
        return try {
            val shellFile = File(context.filesDir, "shells/$shellId/shell.json")
            if (!shellFile.isFile) {
                Log.w(TAG, "shell.json not found for: $shellId")
                return null
            }
            parseShellJson(shellFile.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load shell: $shellId", e)
            null
        }
    }

    private fun getActiveShellId(context: Context, characterId: String): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX$characterId", null)
    }

    /**
     * shell.jsonをパースしてShellオブジェクトを返す。
     * 必須フィールド欠損時はnullを返す。
     */
    fun parseShellJson(jsonStr: String): Shell? {
        return try {
            val json = JSONObject(jsonStr)

            val id = json.optString("id", "").ifBlank { return null }
            val name = json.optString("name", "").ifBlank { return null }
            val targetCharacterId = json.optString("targetCharacterId", "")
            val isR18 = json.optBoolean("r18", false)

            val emotionMappingObj = json.optJSONObject("emotionMapping") ?: return null
            val emotionMapping = buildMap {
                emotionMappingObj.keys().forEach { key ->
                    put(key, emotionMappingObj.getString(key))
                }
            }

            Shell(
                id = id,
                name = name,
                targetCharacterId = targetCharacterId,
                emotionMapping = emotionMapping,
                isR18 = isR18
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse shell.json", e)
            null
        }
    }
}
