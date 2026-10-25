package com.minicore.bookshelf.core.designsystem

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF2: reads the committed TTF's raw `sfnt` table directory and `name` table, with no external
 * font library, to fail loudly if a variable font (has `fvar`) or the wrong weight gets
 * committed in place of the static Inter Regular instance (plan section 3 / Type.kt). Mirrors
 * [FrauncesFontFileTest].
 */
class InterFontFileTest {

    private val fontFile = File("src/main/res/font/inter_regular.ttf")

    @Test
    fun committedFontFile_hasNoFvarTable() {
        assertTrue("expected ${fontFile.path} to exist", fontFile.exists())

        val tags = readSfntTableTags(fontFile)

        assertFalse(
            "a variable font (fvar table present) must not be committed as a static weight",
            tags.contains("fvar")
        )
    }

    @Test
    fun committedFontFile_nameTableIdentifiesInterRegular() {
        assertTrue("expected ${fontFile.path} to exist", fontFile.exists())

        val nameStrings = readNameTableStrings(fontFile)

        assertTrue(
            "expected an 'Inter' family name, found: $nameStrings",
            nameStrings.any { it.contains("Inter", ignoreCase = true) }
        )
        assertTrue(
            "expected a 'Regular' style/weight name, found: $nameStrings",
            nameStrings.any { it.contains("Regular", ignoreCase = true) }
        )
    }

    private fun readSfntTableTags(file: File): Set<String> {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(4)
            val numTables = raf.readUnsignedShort()
            raf.seek(12)
            val tags = mutableSetOf<String>()
            repeat(numTables) {
                val tagBytes = ByteArray(4)
                raf.readFully(tagBytes)
                tags += String(tagBytes, StandardCharsets.US_ASCII)
                raf.skipBytes(12) // checksum + offset + length
            }
            return tags
        }
    }

    private fun readNameTableStrings(file: File): List<String> {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(4)
            val numTables = raf.readUnsignedShort()
            raf.seek(12)

            var nameTableOffset = -1
            repeat(numTables) {
                val tagBytes = ByteArray(4)
                raf.readFully(tagBytes)
                val tag = String(tagBytes, StandardCharsets.US_ASCII)
                raf.skipBytes(4) // checksum
                val offset = raf.readInt()
                raf.skipBytes(4) // length
                if (tag == "name") nameTableOffset = offset
            }

            check(nameTableOffset >= 0) { "no 'name' table found in $file" }

            raf.seek(nameTableOffset.toLong() + 2) // skip format
            val count = raf.readUnsignedShort()
            val stringOffsetFromTableStart = raf.readUnsignedShort()

            val records = (0 until count).map {
                NameRecord(
                    platformId = raf.readUnsignedShort(),
                    encodingId = raf.readUnsignedShort(),
                    languageId = raf.readUnsignedShort(),
                    nameId = raf.readUnsignedShort(),
                    length = raf.readUnsignedShort(),
                    offset = raf.readUnsignedShort(),
                )
            }

            val storageStart = nameTableOffset + stringOffsetFromTableStart
            return records.map { record ->
                raf.seek((storageStart + record.offset).toLong())
                val bytes = ByteArray(record.length)
                raf.readFully(bytes)
                val charset =
                    if (record.platformId == 1) StandardCharsets.US_ASCII else StandardCharsets.UTF_16BE
                String(bytes, charset)
            }
        }
    }

    private data class NameRecord(
        val platformId: Int,
        val encodingId: Int,
        val languageId: Int,
        val nameId: Int,
        val length: Int,
        val offset: Int,
    )
}
