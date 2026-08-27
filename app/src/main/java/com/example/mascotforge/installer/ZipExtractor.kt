package com.example.mascotforge.installer

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
                    // ----- 🛡️ 修正: 末尾スラッシュなしディレクトリエントリ対策 -----
                                        // サイズ0 のエントリはディレクトリとみなす（キャラクターZIPでは空ファイル不要）
                                        // Note: 圧縮方式が DEFLATED でサイズ0のディレクトリエントリを作成する
                                        //  ZIPツール（一部のPythonライブラリ等）に対応するため、メソッドはチェックしない
                                        if (entry.size == 0L) {
                                            destFile.mkdirs()
                                            Log.w(TAG, "サイズ0エントリをディレクトリとして扱います: ${entry.name} (method=${entry.method})")
                                            zip.closeEntry()
                                            entry = zip.nextEntry
                                            fileCount-- // fileCount++ を打ち消す
                                            continue
                                        }
                                        // ----- 修正ここまで -----
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
                totalRead += bytesRead

                // 単一ファイルのランタイムサイズチェック（ヘッダー偽装でバイパス不可）
                if (totalRead > ZipSecurityValidator.MAX_SINGLE_FILE_SIZE) {
                    destFile.delete()
                    throw SecurityException(ZipSecurityValidator.ERR_LIMIT_SINGLE_SIZE)
                }

                // Validatorに展開バイト数を報告し、累積合計サイズを監視
                try {
                    validator.trackUncompressedSize(bytesRead)
                } catch (e: SecurityException) {
                    destFile.delete()
                    throw e
                }

                output.write(buffer, 0, bytesRead)
            }
        }

        return totalRead
    }

    /**
     * ZIP展開先から character.json を探す。
     *
     * キャラクター配布物には README やリリース用フォルダが同梱されることが
     * あるため、トップレベルの配置には依存しない。ZIP の深さ・エントリ数は
     * 展開時に検証済みなので、その範囲で探索する。大文字・小文字だけが
     * 異なる character.json が複数ある場合は、ファイルシステムによる扱いの
     * 違いを避けるため拒否する。
     */
    fun findMetadataFile(dir: File): File? {
        val metadataFiles = dir.walkTopDown()
            .maxDepth(ZipSecurityValidator.MAX_DIRECTORY_DEPTH + 1)
            .filter { it.isFile && it.name.equals("character.json", ignoreCase = true) }
            .toList()

        if (metadataFiles.size > 1) {
            throw SecurityException("DUPLICATE_METADATA_FILES")
        }
        return metadataFiles.singleOrNull()
    }
}
