package com.example.mascotforge.installer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.mascotforge.character.CharacterMetadata
import com.example.mascotforge.character.SafeCharacterLoader
import com.example.mascotforge.characters.CharacterRegistry
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CharacterInstaller(private val context: Context) {

    companion object {
        private const val TAG = "CharacterInstaller"
    }

    private val installedCharDir: File
        get() = File(context.filesDir, "installed_characters").apply {
            if (!exists()) mkdirs()
        }

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
                Result.success(validateAndInstall(input))
            } ?: Result.failure(CharacterInstallException("FILE_OPEN_FAILED", "ファイルを開けませんでした。"))
        } catch (e: CharacterInstallException) {
            Log.e(TAG, "Character installation rejected: ${e.code}", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security violation during installation: ${e.message}", e)
            Result.failure(CharacterInstallException.fromSecurityException(e))
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed due to unexpected error", e)
            Result.failure(
                CharacterInstallException(
                    code = "UNEXPECTED_INSTALL_ERROR",
                    message = "インストール中に予期しないエラーが発生しました。",
                    detail = e.message,
                    cause = e
                )
            )
        }
    }

    private fun validateAndInstall(zipInput: InputStream): CharacterInstallInfo {
        val tempDir = File(context.cacheDir, "char_temp_${System.currentTimeMillis()}")
        if (!tempDir.mkdirs()) {
            throw SecurityException("TEMP_DIR_CREATE_FAILED")
        }

        val validator = ZipSecurityValidator(tempDir)
        val extractor = ZipExtractor(validator)

        try {
            val extractedFiles = extractor.extractZipSecurely(zipInput, tempDir)

            val metadataFile = extractor.findMetadataFile(tempDir)
                ?: throw SecurityException("MISSING_METADATA")

            val metadata = loadMetadata(metadataFile)
            rejectDuplicateId(metadata.id)

            val targetDir = File(installedCharDir, metadata.id)
            synchronized(this) {
                if (targetDir.exists()) {
                    throw CharacterInstallException(
                        code = "ID_ALREADY_EXISTS",
                        message = "同じIDのキャラクターがすでにインストールされています。",
                        detail = metadata.id
                    )
                }

                validator.validateRequiredFiles(tempDir)
                validator.validateImageFiles(tempDir)

                if (!atomicInstall(tempDir, targetDir)) {
                    throw SecurityException("ATOMIC_INSTALL_FAILED")
                }
            }

            Log.i(TAG, "Character installed: ${metadata.id}")
            return CharacterInstallInfo(
                id = metadata.id,
                name = metadata.name,
                version = metadata.version,
                author = metadata.author,
                fileCount = extractedFiles,
                installPath = "[protected]"
            )
        } finally {
            tempDir.deleteRecursively()
        }
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

    private fun loadMetadata(file: File): CharacterMetadata {
        return try {
            if (!file.isFile) {
                throw CharacterInstallException("METADATA_FILE_NOT_FOUND", "character.json が見つかりません。", file.path)
            }
            if (file.length() > ZipSecurityValidator.MAX_SINGLE_FILE_SIZE) {
                throw CharacterInstallException("METADATA_TOO_LARGE", "character.json のサイズが大きすぎます。", file.path)
            }
            // 空ファイルチェック
            if (file.length() == 0L) {
                throw CharacterInstallException(
                    "METADATA_EMPTY",
                    "character.json が空です。ZIPにディレクトリエントリが混入している可能性があります。",
                    file.path
                )
            }

            val metadata = SafeCharacterLoader(context).parseMetadata(file.readText(Charsets.UTF_8))
            if (!ZipSecurityValidator.isValidCharacterId(metadata.id)) {
                throw CharacterInstallException("INVALID_CHARACTER_ID_FORMAT", "キャラクターIDの形式が不正です。", metadata.id)
            }
            metadata
        } catch (e: CharacterInstallException) {
            throw e
        } catch (e: SecurityException) {
            throw CharacterInstallException.fromSecurityException(e)
        } catch (e: Exception) {
            Log.e(TAG, "Metadata parsing error", e)
            throw CharacterInstallException("METADATA_PARSE_FAILED", "character.json の読み込みに失敗しました。", e.message, e)
        }
    }

    private fun rejectDuplicateId(characterId: String) {
        val existing = CharacterRegistry.getEntries(context)
            .firstOrNull { it.metadata.id == characterId }
            ?: return

        if (existing.isBuiltIn) {
            throw CharacterInstallException(
                code = "BUILTIN_ID_ALREADY_EXISTS",
                message = "内蔵キャラクターと同じIDは使用できません。",
                detail = characterId
            )
        }

        throw CharacterInstallException(
            code = "ID_ALREADY_EXISTS",
            message = "同じIDのキャラクターがすでにインストールされています。",
            detail = characterId
        )
    }

    fun uninstall(charId: String): Boolean {
        val validator = ZipSecurityValidator(installedCharDir)

        if (!ZipSecurityValidator.isValidCharacterId(charId)) {
            Log.e(TAG, "Uninstall failed: Invalid character ID format.")
            return false
        }

        val charDir = File(installedCharDir, charId)
        if (!charDir.exists()) {
            return false
        }

        if (!validator.isWithinDirectorySafe(charDir, installedCharDir)) {
            Log.e(TAG, "Uninstall path traversal attempt detected.")
            return false
        }

        return try {
            charDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed: $charId", e)
            false
        }
    }

    fun exportToZip(charId: String, outputUri: Uri): Result<File> {
        return try {
            val validator = ZipSecurityValidator(installedCharDir)

            if (!ZipSecurityValidator.isValidCharacterId(charId)) {
                return Result.failure(SecurityException("INVALID_CHARACTER_ID_FORMAT"))
            }

            val charDir = File(installedCharDir, charId)
            if (!charDir.exists() || !validator.isWithinDirectorySafe(charDir, installedCharDir)) {
                return Result.failure(Exception("CHARACTER_NOT_FOUND"))
            }

            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                zipDirectory(charDir, output, validator)
                Result.success(charDir)
            } ?: Result.failure(Exception("OUTPUT_STREAM_FAILED"))
        } catch (e: SecurityException) {
            Log.e(TAG, "Export security violation", e)
            Result.failure(CharacterInstallException.fromSecurityException(e))
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        }
    }

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

    fun getInstalledMetadata(): List<CharacterMetadata> {
        return CharacterRegistry.getInstalledMetadata(context)
    }
}

data class CharacterInstallInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val fileCount: Int,
    val installPath: String
)

class CharacterInstallException(
    val code: String,
    message: String,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(if (detail.isNullOrBlank()) message else "$message ($detail)", cause) {

    companion object {
        fun fromSecurityException(e: SecurityException): CharacterInstallException {
            val code = e.message ?: "SECURITY_VALIDATION_FAILED"
            val message = when {
                code == "ID_ALREADY_EXISTS" -> "同じIDのキャラクターがすでにインストールされています。"
                code == "INVALID_URI_SCHEME" -> "このファイルのURI形式は使用できません。"
                code == "MISSING_METADATA" || code.startsWith("MISSING_REQUIRED_FILE") -> "必要な character.json が見つかりません。"
                code == "INVALID_CHARACTER_ID_FORMAT" || code == "INVALID_ID_FORMAT" -> "キャラクターIDの形式が不正です。"
                code == ZipSecurityValidator.ERR_JSON_INVALID -> "character.json のJSON形式が不正です。"
                code == ZipSecurityValidator.ERR_IMAGES_NOT_OBJECT -> "character.json の images はオブジェクトである必要があります。"
                code == ZipSecurityValidator.ERR_SPEECH_RULES_NOT_ARRAY -> "character.json の speechRules は配列である必要があります。"
                code == ZipSecurityValidator.ERR_SPEECH_RULE_NO_FILE -> "speechRules に file または files が必要です。"
                code.startsWith("JSON_REFERENCED_FILE_MISSING") -> "character.json が参照しているファイルが見つかりません。"
                code == ZipSecurityValidator.ERR_JSON_TRAVERSAL -> "character.json の参照パスが不正です。"
                code == ZipSecurityValidator.ERR_EXTENSION -> "ZIP内に許可されていない拡張子のファイルがあります。"
                code == ZipSecurityValidator.ERR_PATH_TRAVERSAL -> "ZIP内に危険なパスが含まれています。"
                else -> "キャラクターZIPの検証に失敗しました。"
            }

            return CharacterInstallException(code, message, e.message, e)
        }
    }
}
