package com.example.mascotforge.installer

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.zip.ZipInputStream

/**
 * ZIPの種類（キャラクター or Shell）を自動判別してインストールを委譲する。
 *
 * 判別ロジック:
 *   - ZIPエントリを走査し "character.json" を発見 → CharacterInstaller へ
 *   - ZIPエントリを走査し "shell.json" を発見      → ShellInstaller へ
 *   - どちらも見つからない                         → エラー
 *
 * 既存の CharacterInstaller / ShellInstaller はそのまま残し、
 * CommonInstaller がラップする形で一本化する。
 */
class CommonInstaller(private val context: Context) {

    companion object {
        private const val TAG = "CommonInstaller"
    }

    /**
     * ZIPをインストールする。
     *
     * @return InstallResult.Character または InstallResult.Shell を包んだ Result。
     *         ShellでR18フラグが立っている場合は InstallResult.Shell.isR18 = true。
     *         呼び出し元がユーザーへの警告表示を担当すること。
     */
    fun install(zipUri: Uri): Result<InstallResult> {
        return try {
            when (detectZipType(zipUri)) {
                ZipType.CHARACTER -> {
                    CharacterInstaller(context).installFromZip(zipUri)
                        .map { InstallResult.Character(it) }
                }
                ZipType.SHELL -> {
                    ShellInstaller(context).installFromZip(zipUri)
                        .map { InstallResult.Shell(it) }
                }
                ZipType.UNKNOWN -> {
                    Result.failure(
                        Exception(
                            "ZIPの種類を特定できませんでした。" +
                            "character.json または shell.json が必要です。"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CommonInstaller failed", e)
            Result.failure(e)
        }
    }

    /**
     * ZIPを一度だけ走査してエントリ名から種類を判別する。
     * 実際の展開は行わないため軽量。
     */
    private fun detectZipType(uri: Uri): ZipType {
        if (uri.scheme !in listOf("content", "file")) return ZipType.UNKNOWN

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name.trim()) {
                            "character.json" -> return ZipType.CHARACTER
                            "shell.json"     -> return ZipType.SHELL
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    ZipType.UNKNOWN
                }
            } ?: ZipType.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Failed to peek ZIP type", e)
            ZipType.UNKNOWN
        }
    }
}

/** インストール結果を表すsealed class */
sealed class InstallResult {
    data class Character(val info: CharacterInstallInfo) : InstallResult()
    data class Shell(val info: ShellInstallInfo) : InstallResult()
}

private enum class ZipType { CHARACTER, SHELL, UNKNOWN }
