package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReadStylePackageManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `imports md3 fields and installs bundled resources`() {
        val parent = temporaryFolder.newFolder("packages")
        val zip = zipOf(
            "readConfig.json" to """
                {
                  "name":"fixture",
                  "bgType":2,
                  "bgStr":"background.jpg",
                  "textBold":500,
                  "underline":true,
                  "dottedLine":true,
                  "futureMd3Field":{"value":1},
                  "highlightRules":[{
                    "id":"quote",
                    "name":"dialogue",
                    "pattern":"[“”]",
                    "enabled":true,
                    "position":1,
                    "textColor":-123,
                    "bgImage":"highlight.png"
                  }]
                }
            """.trimIndent().toByteArray(),
            "background.jpg" to byteArrayOf(1, 2, 3),
            "highlight.png" to byteArrayOf(4, 5, 6),
        )

        val result = ReadStylePackageManager.import(
            ByteArrayInputStream(zip),
            "fixture-hash",
            parent,
        )

        assertEquals("md3-read-style", result.sourceFormat)
        assertEquals(500, result.config.textBold)
        assertTrue(result.config.underline)
        assertTrue(File(result.config.bgStr).isFile)
        assertTrue(File(result.config.highlightRules.single().bgImage!!).isFile)
        assertTrue("futureMd3Field" in result.config.ngUnknownFields)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `rejects path traversal without leaving installed files`() {
        val parent = temporaryFolder.newFolder("packages")
        val zip = zipOf(
            "readConfig.json" to "{}".toByteArray(),
            "../outside.txt" to "bad".toByteArray(),
        )

        val error = runCatching {
            ReadStylePackageManager.import(ByteArrayInputStream(zip), "bad-hash", parent)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertFalse(File(parent, "bad-hash").exists())
        assertFalse(File(parent.parentFile, "outside.txt").exists())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }
}
