package com.example.mascotforge.installer

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * キャラクターのインストール・アンインストールを担当 (最終版)
 */
class CharacterInstaller(private val context: Context) {

    companion object {
        private const val TAG = "CharacterInstaller"
    }

    private val installedCharDir: File
        get() = File(context.filesDir, "installed_characters").apply {
            if (!exists()) mkdirs()
        }

    /**
     * ZIPからキャラクターをインストール
     */
    fun installFromZip(zipUri: Uri): Result<CharacterInstallInfo> {
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
            Log.e(TAG, "Security violation during installation: ${e.message}", e)
            Result.failure(Exception("セキュリティ検証に失敗しました。ファイルが不正です。"))
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed due to unexpected error", e)
            Result.failure(Exception("インストール中に予期せぬエラーが発生しました。"))
        }
    }

    /**
     * 検証とインストールの実行
     */
    private fun validateAndInstall(zipInput: InputStream): CharacterInstallInfo {
        val tempDir = File(context.cacheDir, "char_temp_${System.currentTimeMillis()}")
        if (!tempDir.mkdirs()) {
            throw SecurityException("TEMP_DIR_CREATE_FAILED")
        }

        // Validator/Extractorの初期化
        val validator = ZipSecurityValidator(tempDir)
        val extractor = ZipExtractor(validator)

        try {
            val extractedFiles = extractor.extractZipSecurely(zipInput, tempDir)

            val metadataFile = extractor.findMetadataFile(tempDir)
                ?: throw SecurityException("MISSING_METADATA")

            val metadata = loadMetadata(metadataFile)
                ?: throw SecurityException("METADATA_READ_FAILED")

            val targetDir = File(installedCharDir, metadata.id)
            synchronized(this) {
                if (targetDir.exists()) {
                    throw SecurityException("ID_ALREADY_EXISTS")
                }

                // 必須ファイル、JSONスキーマ、画像マジックバイトの最終検証
                validator.validateRequiredFiles(tempDir)
                validator.validateImageFiles(tempDir)

                if (!atomicInstall(tempDir, targetDir)) {
                    throw SecurityException("ATOMIC_INSTALL_FAILED")
                }
            }

            Log.i(TAG, "キャラクターインストール成功: ${metadata.id}")
            return CharacterInstallInfo(
                id = metadata.id,
                name = metadata.name,
                version = metadata.version,
                author = metadata.author,
                fileCount = extractedFiles,
                installPath = "[保護されたパス]"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * アトミックなインストール（中断時も安全）
     */
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
     * メタデータの読み込みとパース
     */
    private fun loadMetadata(file: File): CharacterMetadata? {
        return try {
            if (!file.isFile) return null
            if (file.length() > ZipSecurityValidator.MAX_SINGLE_FILE_SIZE) return null

            val jsonStr = file.readText()
            parseMetadata(jsonStr)
        } catch (e: SecurityException) {
            Log.e(TAG, "Metadata parsing security failure", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Metadata parsing error", e)
            null
        }
    }

    /**
     * メタデータのパース
     */
    private fun parseMetadata(jsonStr: String): CharacterMetadata {
        val json = JSONObject(jsonStr)
        val id = json.getString("id")

        // 修正済み: 静的メソッドとしてクラス名で呼び出す
        if (!ZipSecurityValidator.isValidCharacterId(id)) {
            throw SecurityException("INVALID_CHARACTER_ID_FORMAT")
        }

        return CharacterMetadata(
            id = id,
            name = json.getString("name").sanitizeLogField(),
            version = json.optString("version", "1.0.0"),
            author = json.optString("author", "Unknown").sanitizeLogField(),
            description = json.optString("description", "").sanitizeLogField()
        )
    }

    /**
     * キャラクターのアンインストール
     */
    fun uninstall(charId: String): Boolean {
        // Validatorインスタンスは、isWithinDirectorySafeチェックのためにのみ使用する
        val validator = ZipSecurityValidator(installedCharDir)

        // 🚨 修正箇所: 静的メソッドとしてクラス名で呼び出す
        if (!ZipSecurityValidator.isValidCharacterId(charId)) {
            Log.e(TAG, "Uninstall failed: Invalid character ID format.")
            return false
        }

        val charDir = File(installedCharDir, charId)
        if (!charDir.exists()) {
            return false
        }

        // パストラバーサルチェックはインスタンスメソッドを使用
        if (!validator.isWithinDirectorySafe(charDir, installedCharDir)) {
            Log.e(TAG, "Uninstall path traversal attempt detected.")
            return false
        }

        return try {
            charDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "アンインストール失敗: $charId", e)
            false
        }
    }

    /**
     * キャラクターをZIPにエクスポート
     */
    fun exportToZip(charId: String, outputUri: Uri): Result<File> {
        return try {
            val validator = ZipSecurityValidator(installedCharDir)

            // 静的メソッドとしてクラス名で呼び出す
            if (!ZipSecurityValidator.isValidCharacterId(charId)) {
                return Result.failure(SecurityException("INVALID_CHARACTER_ID_FORMAT"))
            }

            val charDir = File(installedCharDir, charId)
            // パストラバーサルチェックはインスタンスメソッドを使用
            if (!charDir.exists() || !validator.isWithinDirectorySafe(charDir, installedCharDir)) {
                return Result.failure(Exception("CHARACTER_NOT_FOUND"))
            }

            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                zipDirectory(charDir, output, validator)
                Result.success(charDir)
            } ?: Result.failure(Exception("OUTPUT_STREAM_FAILED"))
        } catch (e: SecurityException) {
            Log.e(TAG, "Export security violation", e)
            Result.failure(Exception("エクスポート中にセキュリティ検証に失敗しました。"))
        } catch (e: Exception) {
            Log.e(TAG, "エクスポート失敗", e)
            Result.failure(e)
        }
    }

    /**
     * ディレクトリをZIPに圧縮 (Validatorによる制限チェックを追加)
     */
    private fun zipDirectory(dir: File, output: java.io.OutputStream, validator: ZipSecurityValidator) {
        var entryCount = 0
        ZipOutputStream(output.buffered()).use { zip ->
            dir.walkTopDown()
                .maxDepth(ZipSecurityValidator.MAX_DIRECTORY_DEPTH)
                .filter { it.isFile }
                .forEach { file ->
                    entryCount++
                    if (entryCount > ZipSecurityValidator.MAX_ENTRY_COUNT) {
                        throw SecurityException("ZIP_LIMIT_EXCEEDED_ENTRY_COUNT")
                    }

                    val relativePath = file.relativeTo(dir).path

                    if (relativePath.contains("..") || relativePath.contains('\\')) {
                        throw SecurityException("EXPORT_PATH_INVALID")
                    }

                    zip.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
        }
    }

    /**
     * インストール済みキャラクターのメタデータ一覧を取得
     */
    fun getInstalledMetadata(): List<CharacterMetadata> {
        val validator = ZipSecurityValidator(installedCharDir)

        return installedCharDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { charDir ->
                try {
                    // パストラバーサルチェックはインスタンスメソッドを使用
                    if (!validator.isWithinDirectorySafe(charDir, installedCharDir)) {
                        Log.w(TAG, "Potential path traversal detected in installed directory listing.")
                        return@mapNotNull null
                    }
                    loadMetadata(File(charDir, "character.json"))
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }
}

private fun String.sanitizeLogField(): String =
    this.replace(Regex("[\n\r\t\u0000-\u001F]"), " ").trim()

data class CharacterInstallInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val fileCount: Int,
    val installPath: String
)

data class CharacterMetadata(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String
)