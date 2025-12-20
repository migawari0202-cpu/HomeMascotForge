package com.mascotforge.installer

import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * ZIPファイルの展開を担当 (V2: Validatorと責任を分離)
 *
 * @param validator 状態を持つセキュリティ検証インスタンス
 */
class ZipExtractor(private val validator: ZipSecurityValidator) {

    companion object {
        private const val TAG = "ZipExtractor"
        private const val CHUNK_SIZE = 8192
    }

    /**
     * ZIPを安全に展開
     * @param input ZIPファイルのInputStream
     * @param destDir 展開先ディレクトリ
     * @return 展開したファイル数
     */
    fun extractZipSecurely(input: InputStream, destDir: File): Int {
        var fileCount = 0

        if (!destDir.exists() && !destDir.mkdirs()) {
            throw SecurityException("EXTRACTION_ERROR_DEST_DIR")
        }

        ZipInputStream(input.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry

            while (entry != null) {

                // 1. エントリの検証と正規化をワンステップで実行
                val safeRelativePath = try {
                    validator.validateEntry(entry)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Validation failed for entry: ${entry.name}", e)
                    throw e
                }

                val destFile = File(destDir, safeRelativePath)
                fileCount++

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()

                    val totalRead = saveEntrySecurely(zip, destFile)

                    // ファイル権限の修正 (実行権限の剥奪)
                    if (destFile.isFile) {
                        try {
                            destFile.setExecutable(false, false)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to strip executable permission: ${destFile.name}")
                        }
                    }

                    Log.d(TAG, "展開完了: ${entry.name} (${totalRead}bytes)")
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        Log.i(TAG, "展開成功: $fileCount ファイル, 合計 ${validator.getTotalSize()} bytes")
        return fileCount
    }

    /**
     * エントリを安全に保存し、累積サイズをValidatorに報告
     */
    private fun saveEntrySecurely(zip: ZipInputStream, destFile: File): Long {
        var totalRead = 0L
        val buffer = ByteArray(CHUNK_SIZE)

        destFile.outputStream().buffered().use { output ->
            var bytesRead: Int
            while (zip.read(buffer).also { bytesRead = it } != -1) {

                // Validatorに展開バイト数を報告し、累積サイズと単一ファイルサイズを監視
                try {
                    validator.trackUncompressedSize(bytesRead)
                } catch (e: SecurityException) {
                    // 展開途中のファイルを削除
                    destFile.delete()
                    throw e
                }

                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
            }
        }

        return totalRead
    }

    /**
     * character.jsonを探す
     */
    fun findMetadataFile(dir: File): File? {
        val rootMeta = File(dir, "character.json")
        return if (rootMeta.exists() && rootMeta.isFile) rootMeta else null
    }
}