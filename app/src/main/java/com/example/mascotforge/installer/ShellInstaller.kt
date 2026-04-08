package com.example.mascotforge.installer

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * Shell ZIPのインストール・アンインストールを担当。
 *
 * インストール先: filesDir/shells/{shellId}/
 * メタファイル:   shell.json
 *
 * R18フラグ検出時はShellInstallInfo.isR18 = true で返す。
 * 実際の警告表示は呼び出し元（CommonInstaller / UI）の責任。
 */
class ShellInstaller(private val context: Context) {

    companion object {
        private const val TAG = "ShellInstaller"
    }

    private val installedShellDir: File
        get() = File(context.filesDir, "shells").apply {
            if (!exists()) mkdirs()
        }

    /**
     * ZIPからShellをインストール
     */
    fun installFromZip(zipUri: Uri): Result<ShellInstallInfo> {
        return try {
            if (zipUri.scheme !in listOf("content", "file")) {
                throw SecurityException("INVALID_URI_SCHEME")
            }

            val mimeType = context.contentResolver.getType(zipUri)
            if (mimeType != null && mimeType.contains("text")) {
                Log.w(TAG, "Suspected MIME type: $mimeType")
            }

            context.contentResolver.openInputStream(zipUri)?.use { input ->
                val info = validateAndInstall(input)
                Result.success(info)
            } ?: Result.failure(Exception("FILE_OPEN_FAILED"))
        } catch (e: SecurityException) {
            Log.e(TAG, "Security violation: ${e.message}", e)
            Result.failure(Exception("セキュリティ検証に失敗しました。ファイルが不正です。"))
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            Result.failure(Exception("インストール中に予期せぬエラーが発生しました。"))
        }
    }

    private fun validateAndInstall(zipInput: InputStream): ShellInstallInfo {
        val tempDir = File(context.cacheDir, "shell_temp_${System.currentTimeMillis()}")
        if (!tempDir.mkdirs()) throw SecurityException("TEMP_DIR_CREATE_FAILED")

        val validator = ZipSecurityValidator(tempDir)
        val extractor = ZipExtractor(validator)

        try {
            val extractedFiles = extractor.extractZipSecurely(zipInput, tempDir)

            val shellFile = File(tempDir, "shell.json")
            if (!shellFile.isFile) throw SecurityException("MISSING_SHELL_JSON")

            val shellData = parseAndValidateShellJson(shellFile, tempDir)

            validator.validateImageFiles(tempDir)

            val targetDir = File(installedShellDir, shellData.id)
            synchronized(this) {
                if (targetDir.exists()) throw SecurityException("ID_ALREADY_EXISTS")

                if (!atomicInstall(tempDir, targetDir)) {
                    throw SecurityException("ATOMIC_INSTALL_FAILED")
                }
            }

            Log.i(TAG, "Shell installed: ${shellData.id} (R18=${shellData.isR18})")
            return ShellInstallInfo(
                id = shellData.id,
                name = shellData.name,
                targetCharacterId = shellData.targetCharacterId,
                isR18 = shellData.isR18,
                fileCount = extractedFiles
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * shell.jsonのパースと検証。
     * - id: 必須、キャラクターIDと同じ形式
     * - name: 必須
     * - emotionMapping: 必須オブジェクト。値はimages/以下のファイル名
     * - targetCharacterId: 省略可
     * - r18: 省略可（デフォルトfalse）
     */
    private fun parseAndValidateShellJson(file: File, rootDir: File): ShellData {
        if (file.length() > ZipSecurityValidator.MAX_SINGLE_FILE_SIZE) {
            throw SecurityException("CONFIG_TOO_LARGE")
        }

        val json = try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw SecurityException("INVALID_JSON_FORMAT")
        }

        val id = json.optString("id", "")
        if (!ZipSecurityValidator.isValidCharacterId(id)) {
            throw SecurityException("INVALID_SHELL_ID_FORMAT")
        }

        val name = json.optString("name", "").sanitize().ifBlank {
            throw SecurityException("MISSING_SHELL_NAME")
        }

        val targetCharacterId = json.optString("targetCharacterId", "").sanitize()

        val emotionMappingObj = json.optJSONObject("emotionMapping")
            ?: throw SecurityException("MISSING_EMOTION_MAPPING")

        if (emotionMappingObj.length() == 0) {
            throw SecurityException("EMPTY_EMOTION_MAPPING")
        }

        val emotionMapping = buildMap<String, String> {
            emotionMappingObj.keys().forEach { key ->
                val value = emotionMappingObj.optString(key, "")
                if (value.isBlank() || value.contains("..") ||
                    value.startsWith("/") || value.contains("\\") || value.contains(":")) {
                    throw SecurityException("INVALID_IMAGE_PATH_IN_MAPPING: $key")
                }
                val imageFile = File(rootDir, "images/$value")
                if (!imageFile.isFile) {
                    throw SecurityException("MISSING_IMAGE_FILE: $value")
                }
                put(key, value)
            }
        }

        val isR18 = json.optBoolean("r18", false)
        if (isR18) {
            Log.w(TAG, "R18 shell detected: $id")
        }

        return ShellData(
            id = id,
            name = name,
            targetCharacterId = targetCharacterId,
            emotionMapping = emotionMapping,
            isR18 = isR18
        )
    }

    private fun atomicInstall(source: File, target: File): Boolean {
        val tempTarget = File(target.parentFile, "${target.name}.tmp_${System.currentTimeMillis()}")
        return try {
            source.copyRecursively(tempTarget, overwrite = false)
            if (tempTarget.renameTo(target)) {
                true
            } else {
                tempTarget.deleteRecursively()
                false
            }
        } catch (e: Exception) {
            tempTarget.deleteRecursively()
            throw SecurityException("ATOMIC_INSTALL_IO_FAILED")
        }
    }

    /**
     * Shellをアンインストール
     */
    fun uninstall(shellId: String): Boolean {
        if (!ZipSecurityValidator.isValidCharacterId(shellId)) {
            Log.e(TAG, "Uninstall failed: invalid shell ID format.")
            return false
        }

        val shellDir = File(installedShellDir, shellId)
        if (!shellDir.exists()) return false

        val validator = ZipSecurityValidator(installedShellDir)
        if (!validator.isWithinDirectorySafe(shellDir, installedShellDir)) {
            Log.e(TAG, "Uninstall path traversal attempt detected.")
            return false
        }

        return try {
            shellDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed: $shellId", e)
            false
        }
    }
}

private data class ShellData(
    val id: String,
    val name: String,
    val targetCharacterId: String,
    val emotionMapping: Map<String, String>,
    val isR18: Boolean
)

private fun String.sanitize(): String =
    this.replace(Regex("[\n\r\t\u0000-\u001F]"), " ").trim()

data class ShellInstallInfo(
    val id: String,
    val name: String,
    val targetCharacterId: String,
    val isR18: Boolean,
    val fileCount: Int
)
