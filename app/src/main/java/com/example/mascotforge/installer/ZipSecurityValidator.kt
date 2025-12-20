package com.mascotforge.installer

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry

class ZipSecurityValidator(
    private val destinationDir: File
) {

    companion object {
        private const val TAG = "ZipSec"

        // セキュリティ制限
        const val MAX_TOTAL_UNCOMPRESSED_SIZE = 50L * 1024 * 1024
        const val MAX_SINGLE_FILE_SIZE = 10L * 1024 * 1024
        const val MAX_ENTRY_COUNT = 500
        const val MAX_DIRECTORY_DEPTH = 5

        // エラー定数 (ログ解析性向上のため定数化)
        const val ERR_LIMIT_ENTRY_COUNT = "ZIP_LIMIT_EXCEEDED_ENTRY_COUNT"
        const val ERR_INVALID_ENTRY_NAME = "INVALID_ENTRY_NAME"
        const val ERR_PATH_TRAVERSAL = "PATH_TRAVERSAL_ATTEMPT"
        const val ERR_SYSTEM_FILE = "SYSTEM_FILE_NOT_ALLOWED"
        const val ERR_DIR_DEPTH = "DIR_DEPTH_EXCEEDED"
        const val ERR_SYMLINK = "SYMLINK_NOT_ALLOWED"
        const val ERR_EXTENSION = "EXTENSION_NOT_ALLOWED"
        const val ERR_EXTENSION_REQUIRED = "EXTENSION_REQUIRED"
        const val ERR_DOUBLE_EXTENSION = "DANGEROUS_DOUBLE_EXTENSION"
        const val ERR_LIMIT_SINGLE_SIZE = "ZIP_LIMIT_EXCEEDED_SINGLE_SIZE_HEADER"
        const val ERR_LIMIT_TOTAL_SIZE = "ZIP_LIMIT_EXCEEDED_TOTAL_SIZE_HEADER"
        const val ERR_LIMIT_TOTAL_SIZE_RUNTIME = "ZIP_LIMIT_EXCEEDED_TOTAL_SIZE_RUNTIME"
        const val ERR_JSON_INVALID = "INVALID_JSON_FORMAT"
        const val ERR_JSON_PATH_TYPE = "JSON_PATH_VALUE_NOT_STRING"
        const val ERR_JSON_TRAVERSAL = "JSON_PATH_TRAVERSAL_OR_MISSING"

        private val ALLOWED_EXTENSIONS = setOf("json", "txt", "png", "jpg", "jpeg", "webp", "gif")
        private val DANGEROUS_EXTENSIONS = setOf("apk", "dex", "so", "jar", "class", "exe", "sh", "bat", "js", "html", "htm", "php")

        // Unix file type masks
        private const val FILE_TYPE_FLAG = 0xF000
        private const val LINK_FLAG = 0xA000

        /**
         * キャラクターIDの検証 (静的メソッド)
         */
        fun isValidCharacterId(id: String): Boolean {
            return id.matches(Regex("^[a-zA-Z0-9_-]{1,50}$"))
        }
    }

    // 状態管理
    private var currentEntryCount = 0
    private var currentTotalUncompressedSize = 0L

    // canonicalDirのキャッシュ
    private val canonicalDestinationDir: String = try {
        destinationDir.canonicalPath
    } catch (e: Exception) {
        Log.e(TAG, "Destination directory canonical path resolution failed", e)
        throw IllegalStateException("Initialization failed: Invalid destination path.")
    }

    fun getTotalSize(): Long = currentTotalUncompressedSize

    /**
     * ZIPエントリのヘッダー検証と累積状態の更新
     */
    fun validateEntry(entry: ZipEntry): String {
        // 1. 全体制限チェック (エントリ数)
        currentEntryCount++
        if (currentEntryCount > MAX_ENTRY_COUNT) {
            throw SecurityException(ERR_LIMIT_ENTRY_COUNT)
        }

        val rawName = entry.name.trim()

        // 2. パス名の基本チェック
        if (rawName.isBlank() || rawName.length > 255 || rawName.any { it.code == 0 || (it.code < 32 && it !in listOf('\n', '\r', '\t')) }) {
            throw SecurityException(ERR_INVALID_ENTRY_NAME)
        }

        // 3. パストラバーサル/Zip Slip検出 (Canonical Path)
        val targetFile = File(destinationDir, rawName)
        val canonicalFilePath = try {
            targetFile.canonicalPath
        } catch (e: Exception) {
            throw SecurityException("INVALID_PATH_RESOLUTION")
        }

        val normalizedDirPathPrefix = canonicalDestinationDir + File.separator

        if (!canonicalFilePath.startsWith(normalizedDirPathPrefix)) {
            // ディレクトリ自体のパスと完全に一致する場合を除き例外
            if (canonicalFilePath != canonicalDestinationDir) {
                throw SecurityException(ERR_PATH_TRAVERSAL)
            }
        }

        val safeRelativePath = canonicalFilePath.removePrefix(normalizedDirPathPrefix)
            .replace('\\', '/')

        // 4. システムファイル/隠しファイルのチェック
        val components = safeRelativePath.split('/')
        for (component in components) {
            if (component.startsWith(".") || component == "__MACOSX") {
                throw SecurityException(ERR_SYSTEM_FILE)
            }
        }

        // 5. ディレクトリの深さチェック (【改善】Splitベースで正確に計算)
        validateDirectoryDepth(safeRelativePath)

        // 6. Symlink検出
        if (isSymlink(entry)) {
            throw SecurityException(ERR_SYMLINK)
        }

        if (entry.isDirectory) {
            return safeRelativePath
        }

        // 7. ファイル拡張子のチェック (【改善】拡張子なしを拒否)
        validateFileExtension(safeRelativePath)

        // 8. サイズチェック (【改善】ヘッダー情報は参考値として扱い、過信しない)
        if (entry.size != -1L) {
            // Note: entry.size は攻撃者によって偽装可能なため、ここでのチェックは目安。
            // 実際の強制力は trackUncompressedSize() にある。
            if (entry.size > MAX_SINGLE_FILE_SIZE) {
                throw SecurityException(ERR_LIMIT_SINGLE_SIZE)
            }
            if (currentTotalUncompressedSize + entry.size > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                throw SecurityException(ERR_LIMIT_TOTAL_SIZE)
            }
        }

        return safeRelativePath
    }

    /**
     * 展開されたバイト数を累積し、制限を超えていないか監視する (Zip Bomb対策の核心)
     */
    fun trackUncompressedSize(bytesRead: Int) {
        currentTotalUncompressedSize += bytesRead
        if (currentTotalUncompressedSize > MAX_TOTAL_UNCOMPRESSED_SIZE) {
            throw SecurityException(ERR_LIMIT_TOTAL_SIZE_RUNTIME)
        }
    }

    private fun isSymlink(entry: ZipEntry): Boolean {
        // 標準APIでの検出が困難なため、ヒューリスティックな検出を行う
        // Note: より確実な検出には Commons Compress の利用を推奨
        try {
            val isSymlinkMethod = entry.javaClass.methods.firstOrNull { it.name == "isUnixSymlink" && it.parameterTypes.isEmpty() }
            if (isSymlinkMethod != null) {
                return isSymlinkMethod.invoke(entry) as? Boolean == true
            }
        } catch (ignore: Exception) {}

        try {
            val getUnixModeMethod = entry.javaClass.methods.firstOrNull { it.name == "getUnixMode" && it.parameterTypes.isEmpty() }
            if (getUnixModeMethod != null) {
                val mode = (getUnixModeMethod.invoke(entry) as? Int) ?: 0
                if ((mode and FILE_TYPE_FLAG) == LINK_FLAG) return true
            }
        } catch (ignore: Exception) {}

        return false
    }

    /**
     * ディレクトリの深さをチェック (【改善】Splitして正確にカウント)
     */
    private fun validateDirectoryDepth(relativePath: String) {
        // 空文字を除外してコンポーネント化 ("a//b/" -> ["a", "b"])
        val components = relativePath.split('/').filter { it.isNotEmpty() }

        // ファイルパス自体もcomponentsに含まれるため、ディレクトリ深度は size - 1
        // 例: "a/b/c.txt" -> ["a", "b", "c.txt"] -> size 3 -> depth 2
        val dirDepth = if (components.isEmpty()) 0 else components.size - 1

        if (dirDepth > MAX_DIRECTORY_DEPTH) {
            throw SecurityException(ERR_DIR_DEPTH)
        }
    }

    /**
     * ファイル拡張子の検証
     */
    private fun validateFileExtension(relativePath: String) {
        val fileName = relativePath.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', "").lowercase()

        // 【改善】拡張子なし("")は不許可にする
        if (extension.isEmpty()) {
            throw SecurityException(ERR_EXTENSION_REQUIRED)
        }
        if (extension !in ALLOWED_EXTENSIONS) {
            throw SecurityException(ERR_EXTENSION)
        }

        val parts = fileName.split('.')
        // 二重拡張子のチェック (例: evil.exe.jpg)
        if (parts.size > 2) {
            val middleParts = parts.subList(1, parts.size - 1)
            for (part in middleParts) {
                if (part.lowercase() in DANGEROUS_EXTENSIONS) {
                    throw SecurityException(ERR_DOUBLE_EXTENSION)
                }
            }
        }
    }

    fun isWithinDirectorySafe(file: File, directory: File): Boolean {
        val canonicalFile = try { file.canonicalFile } catch (e: Exception) { return false }
        val canonicalDir = try { directory.canonicalFile } catch (e: Exception) { return false }
        val filePath = canonicalFile.path
        val dirPath = canonicalDir.path

        return filePath.startsWith(dirPath + File.separator) || filePath == dirPath
    }

    fun verifyImageMagicBytes(input: InputStream, extension: String): Boolean {
        val header = ByteArray(16)
        input.mark(16)
        val read = input.read(header)
        input.reset()

        if (read < 4) return false

        val isValid = when (extension.lowercase()) {
            "png" -> header.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            "jpg", "jpeg" -> header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
            "gif" -> header.take(4).toByteArray().contentEquals(byteArrayOf(0x47, 0x49, 0x46, 0x38))
            "webp" -> {
                // RIFF....WEBP の構造をチェック
                (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()) &&
                        (read >= 12 && header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte())
            }
            else -> true
        }

        if (!isValid) Log.e(TAG, "Magic byte validation failed for extension: $extension")
        return isValid
    }

    // --- 展開後のファイル構造・コンテンツ検証 ---

    fun validateRequiredFiles(rootDir: File) {
        val charFile = File(rootDir, "character.json")
        if (!charFile.exists()) throw SecurityException("MISSING_REQUIRED_FILE: character.json")
        validateCharacterJson(charFile, rootDir)
    }

    fun validateCharacterJson(file: File, rootDir: File) {
        if (file.length() > MAX_SINGLE_FILE_SIZE) throw SecurityException("CONFIG_TOO_LARGE")

        val text = try { file.readText(Charsets.UTF_8) } catch (e: Exception) { throw SecurityException("CONFIG_READ_ERROR") }
        val json = try { JSONObject(text) } catch (e: Exception) { throw SecurityException(ERR_JSON_INVALID) }

        // required: id
        val id = json.getString("id")
        if (!Companion.isValidCharacterId(id)) throw SecurityException("INVALID_ID_FORMAT")

        // 【改善】ヘルパー関数: JSON内のパス検証をCanonical Pathで厳格化
        fun validateJsonPath(path: String, prefix: String) {
            // 文字列レベルのチェックは念のため残すが、主役はcanonical check
            if (path.contains("..") || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
                throw SecurityException(ERR_JSON_TRAVERSAL)
            }

            val targetFile = File(rootDir, "$prefix/$path")

            // 実際のファイルシステム上の正規化パスで比較 (isWithinDirectorySafeと同等のロジックを強制適用)
            // ここでファイルが存在するかどうかも同時にチェック
            if (!targetFile.isFile) {
                throw SecurityException("JSON_REFERENCED_FILE_MISSING: $path")
            }

            if (!isWithinDirectorySafe(targetFile, rootDir)) {
                throw SecurityException(ERR_JSON_TRAVERSAL)
            }
        }

        // images
        if (json.has("images")) {
            val images = json.optJSONArray("images") ?: throw SecurityException("IMAGES_MUST_BE_ARRAY")
            for (i in 0 until images.length()) {
                // 【改善】型チェックの強化。optStringではなく生の値を確認する
                val rawVal = images.opt(i)
                if (rawVal !is String) throw SecurityException(ERR_JSON_PATH_TYPE)
                validateJsonPath(rawVal, "images")
            }
        } else {
            throw SecurityException("MISSING_IMAGES_KEY")
        }

        // speeches
        if (json.has("speeches")) {
            val sp = json.get("speeches")
            when (sp) {
                is JSONArray -> {
                    for (i in 0 until sp.length()) {
                        val rawVal = sp.opt(i)
                        if (rawVal !is String) throw SecurityException(ERR_JSON_PATH_TYPE)
                        validateJsonPath(rawVal, "speeches")
                    }
                }
                is JSONObject -> {
                    sp.keys().forEach { k ->
                        val rawVal = sp.opt(k)
                        if (rawVal !is String) throw SecurityException(ERR_JSON_PATH_TYPE)
                        validateJsonPath(rawVal as String, "speeches")
                    }
                }
                else -> throw SecurityException("INVALID_SPEECHES_FORMAT")
            }
        } else {
            throw SecurityException("MISSING_SPEECHES_KEY")
        }

        Log.i(TAG, "Configuration validation passed.")
    }

    fun validateImageFiles(dir: File) {
        val imagesDir = File(dir, "images")
        if (!imagesDir.exists()) return

        val imageFiles = mutableListOf<File>()
        imagesDir.walkTopDown()
            .maxDepth(MAX_DIRECTORY_DEPTH)
            .filter { it.isFile }
            .forEach { imageFiles.add(it) }

        if (imageFiles.size > 100) {
            throw SecurityException("TOO_MANY_IMAGE_FILES")
        }

        imageFiles.forEach { file ->
            val extension = file.extension.lowercase()
            if (extension in listOf("png", "jpg", "jpeg", "webp", "gif")) {
                file.inputStream().buffered().use { input ->
                    if (!verifyImageMagicBytes(input, extension)) {
                        throw SecurityException("INVALID_IMAGE_MAGIC_BYTES: ${file.name}")
                    }
                }
            }
        }
    }
}