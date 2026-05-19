package com.example.mascotforge.installer

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.zip.ZipInputStream

/**
 * ZIPの中身を見て、Character / Shell をインストールする共通入口。
 *
 * ルール:
 * - ZIP内に `character.json` があれば CharacterInstaller
 * - ZIP内に `shell.json` があれば ShellInstaller
 * - 両方入っていれば両方インストールする
 */
class CommonInstaller(private val context: Context) {

    companion object {
        private const val TAG = "CommonInstaller"
    }

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
                ZipType.BOTH -> installCharacterAndShell(zipUri)
                ZipType.UNKNOWN -> Result.failure(
                    Exception(
                        "ZIPの種類を判定できませんでした。character.json または shell.json が必要です。"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "CommonInstaller failed", e)
            Result.failure(e)
        }
    }

    private fun installCharacterAndShell(zipUri: Uri): Result<InstallResult> {
        val characterInstaller = CharacterInstaller(context)
        val shellInstaller = ShellInstaller(context)

        val characterResult = characterInstaller.installFromZip(zipUri)
        if (characterResult.isFailure) {
            return characterResult.map { InstallResult.Character(it) }
        }
        val characterInfo = characterResult.getOrThrow()

        val shellResult = shellInstaller.installFromZip(zipUri)
        if (shellResult.isFailure) {
            // 両方入りZIPとして扱っているので、片方だけ成功した状態を残さない（ベストエフォート）
            runCatching { characterInstaller.uninstall(characterInfo.id) }
            return Result.failure(shellResult.exceptionOrNull() ?: Exception("SHELL_INSTALL_FAILED"))
        }
        val shellInfo = shellResult.getOrThrow()

        return Result.success(InstallResult.Both(character = characterInfo, shell = shellInfo))
    }

    private fun detectZipType(uri: Uri): ZipType {
        if (uri.scheme !in listOf("content", "file")) return ZipType.UNKNOWN

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var hasCharacter = false
                    var hasShell = false

                    var entry = zip.nextEntry
                    while (entry != null) {
                        val fileName = entry.name
                            .replace("\\", "/")
                            .substringAfterLast("/")

                        when (fileName) {
                            "character.json" -> hasCharacter = true
                            "shell.json" -> hasShell = true
                        }
                        if (hasCharacter && hasShell) return ZipType.BOTH
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }

                    when {
                        hasCharacter -> ZipType.CHARACTER
                        hasShell -> ZipType.SHELL
                        else -> ZipType.UNKNOWN
                    }
                }
            } ?: ZipType.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Failed to peek ZIP type", e)
            ZipType.UNKNOWN
        }
    }
}

sealed class InstallResult {
    data class Character(val info: CharacterInstallInfo) : InstallResult()
    data class Shell(val info: ShellInstallInfo) : InstallResult()
    data class Both(val character: CharacterInstallInfo, val shell: ShellInstallInfo) : InstallResult()
}

private enum class ZipType { CHARACTER, SHELL, BOTH, UNKNOWN }
