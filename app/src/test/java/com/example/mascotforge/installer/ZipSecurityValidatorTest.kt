package com.example.mascotforge.installer

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * インストール時 character.json 検証の回帰テスト。
 * speechRules の "speeches/xxx.txt" が二重パスにならないことを保証する。
 */
class ZipSecurityValidatorTest {

    private fun tempRoot(block: (File) -> Unit) {
        val root = File.createTempFile("zip_sec_", null)
        assertTrue(root.delete())
        assertTrue(root.mkdirs())
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeMinimalCharacter(
        root: File,
        speechPathInJson: String,
        physicalSpeechRelative: String = "speeches/default.txt"
    ) {
        File(root, "images").mkdirs()
        File(root, "images/normal.png").writeBytes(ByteArray(8))

        val speechFile = File(root, physicalSpeechRelative)
        speechFile.parentFile?.mkdirs()
        speechFile.writeText("hello\n")

        File(root, "character.json").writeText(
            """
            {
              "id": "test_char",
              "name": "テスト",
              "version": "1.8",
              "images": { "normal": "normal.png" },
              "speechRules": [
                { "file": "$speechPathInJson" }
              ]
            }
            """.trimIndent()
        )
    }

    @Test
    fun validateCharacterJson_acceptsSpeechesPrefixedPath() {
        tempRoot { root ->
            // 記法・デフォルトキャラと同じ "speeches/default.txt"
            writeMinimalCharacter(
                root = root,
                speechPathInJson = "speeches/default.txt",
                physicalSpeechRelative = "speeches/default.txt"
            )
            val validator = ZipSecurityValidator(root)
            validator.validateRequiredFiles(root)
        }
    }

    @Test
    fun validateCharacterJson_acceptsBareSpeechFilename() {
        tempRoot { root ->
            // 互換: ファイル名のみ
            writeMinimalCharacter(
                root = root,
                speechPathInJson = "default.txt",
                physicalSpeechRelative = "speeches/default.txt"
            )
            val validator = ZipSecurityValidator(root)
            validator.validateRequiredFiles(root)
        }
    }

    @Test
    fun validateCharacterJson_acceptsFilesArrayWithSpeechesPrefix() {
        tempRoot { root ->
            File(root, "images").mkdirs()
            File(root, "images/normal.png").writeBytes(ByteArray(8))
            File(root, "speeches").mkdirs()
            File(root, "speeches/a.txt").writeText("a\n")
            File(root, "speeches/b.txt").writeText("b\n")
            File(root, "character.json").writeText(
                """
                {
                  "id": "test_char",
                  "name": "テスト",
                  "images": { "normal": "normal.png" },
                  "speechRules": [
                    { "files": ["speeches/a.txt", "speeches/b.txt"] }
                  ]
                }
                """.trimIndent()
            )
            ZipSecurityValidator(root).validateRequiredFiles(root)
        }
    }

    @Test
    fun validateCharacterJson_missingSpeechFile_throws() {
        tempRoot { root ->
            writeMinimalCharacter(
                root = root,
                speechPathInJson = "speeches/missing.txt",
                physicalSpeechRelative = "speeches/default.txt"
            )
            try {
                ZipSecurityValidator(root).validateRequiredFiles(root)
                fail("expected SecurityException")
            } catch (e: SecurityException) {
                assertTrue(
                    "message should mention missing path: ${e.message}",
                    e.message?.contains("JSON_REFERENCED_FILE_MISSING") == true
                )
                assertTrue(
                    e.message?.contains("speeches/missing.txt") == true
                )
            }
        }
    }

    @Test
    fun validateCharacterJson_doesNotLookInDoubleSpeechesFolder() {
        tempRoot { root ->
            // 旧バグ: speeches/speeches/default.txt だけあっても通してはいけない。
            // 正規の speeches/default.txt が無い → 失敗するべき。
            File(root, "images").mkdirs()
            File(root, "images/normal.png").writeBytes(ByteArray(8))
            File(root, "speeches/speeches").mkdirs()
            File(root, "speeches/speeches/default.txt").writeText("wrong place\n")
            File(root, "character.json").writeText(
                """
                {
                  "id": "test_char",
                  "name": "テスト",
                  "images": { "normal": "normal.png" },
                  "speechRules": [
                    { "file": "speeches/default.txt" }
                  ]
                }
                """.trimIndent()
            )
            try {
                ZipSecurityValidator(root).validateRequiredFiles(root)
                fail("should fail when only double-nested speeches path exists")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("JSON_REFERENCED_FILE_MISSING") == true)
            }
        }
    }
}
