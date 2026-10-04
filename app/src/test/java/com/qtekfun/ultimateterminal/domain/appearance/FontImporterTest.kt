// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FontImporterTest {
    private class FakeStore(var writes: Boolean = true, var probe: FontProbe? = GoodProbe) :
        FontFileStore {
        val files = mutableMapOf<String, ByteArray>()

        override suspend fun write(fileName: String, bytes: ByteArray): Boolean {
            if (writes) files[fileName] = bytes
            return writes
        }

        override suspend fun delete(fileName: String) {
            files.remove(fileName)
        }

        override fun probe(fileName: String) = if (fileName in files) probe else null
    }

    private object GoodProbe : FontProbe {
        override fun loads() = true
        override fun hasGlyphs(text: String) = true
        override fun advance(text: String) = 10f
    }

    private fun probe(loads: Boolean = true, glyphs: Boolean = true, narrowI: Boolean = false) =
        object : FontProbe {
            override fun loads() = loads
            override fun hasGlyphs(text: String) = glyphs
            override fun advance(text: String) = if (narrowI && text == "i") 3f else 10f
        }

    private val ttf = byteArrayOf(0, 1, 0, 0, 9, 9, 9)
    private val otf = "OTTO-data".toByteArray(Charsets.ISO_8859_1)
    private val store = FakeStore()
    private val importer = FontImporter(store) { "abc" }

    private fun reason(outcome: Outcome<ImportedFont>) =
        ((outcome as Outcome.Failure).error as DomainError.InvalidValue).field

    @Test
    fun aTrueTypeFontIsStoredUnderItsId() = runTest {
        val font = (
            importer.import(
                ttf,
                "Fira_Code.ttf",
                emptyList()
            ) as Outcome.Success
            ).value.font

        assertEquals("Fira Code", font.name)
        assertEquals("font-fira-code-abc", font.id)
        assertEquals("font-fira-code-abc.ttf", font.fileName)
        assertEquals(setOf("font-fira-code-abc.ttf"), store.files.keys)
    }

    @Test
    fun anOpenTypeFontGetsTheOtfExtension() = runTest {
        val font = (importer.import(otf, "Hack.otf", emptyList()) as Outcome.Success).value.font

        assertTrue(font.fileName.endsWith(".otf"))
    }

    @Test
    fun theOldTrueTypeTagIsAccepted() = runTest {
        val old = "true....".toByteArray(Charsets.ISO_8859_1)

        assertTrue(importer.import(old, "Old.ttf", emptyList()) is Outcome.Success)
    }

    @Test
    fun somethingThatIsNotAFontIsRefusedAndNothingIsWritten() = runTest {
        val result = importer.import("hello world".toByteArray(), "notes.ttf", emptyList())

        assertEquals("NOT_A_FONT", reason(result))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun aTinyFileIsNotAFont() = runTest {
        assertEquals("NOT_A_FONT", reason(importer.import(byteArrayOf(0, 1), "x.ttf", emptyList())))
    }

    @Test
    fun aHugeFileIsRefusedBeforeAnythingIsWritten() = runTest {
        val huge = ByteArray(FontImporter.MAX_BYTES + 1).also { it[1] = 1 }

        assertEquals("TOO_LARGE", reason(importer.import(huge, "big.ttf", emptyList())))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun aFontThatDoesNotLoadIsDeleted() = runTest {
        store.probe = probe(loads = false)

        assertEquals("UNREADABLE", reason(importer.import(ttf, "Broken.ttf", emptyList())))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun aProportionalFontIsDeleted() = runTest {
        store.probe = probe(narrowI = true)

        assertEquals("NOT_MONOSPACED", reason(importer.import(ttf, "Arial.ttf", emptyList())))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun aFontWithoutGlyphsIsDeleted() = runTest {
        store.probe = probe(glyphs = false)

        assertEquals("MISSING_GLYPHS", reason(importer.import(ttf, "Icons.ttf", emptyList())))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun aFileThatCannotBeWrittenIsReportedAndCleanedUp() = runTest {
        store.writes = false

        assertEquals("IO", reason(importer.import(ttf, "Fira.ttf", emptyList())))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun aFileThatVanishesBeforeTheProbeIsUnreadable() = runTest {
        store.probe = null

        assertEquals("UNREADABLE", reason(importer.import(ttf, "Gone.ttf", emptyList())))
    }

    @Test
    fun aNameAlreadyInUseIsRefusedWithoutWriting() = runTest {
        val existing = listOf(CustomFont("font-x", "Fira Code", "font-x.ttf"))

        val result = importer.import(ttf, "fira_code.ttf", existing)

        assertEquals(DomainError.NameTaken("fira code"), (result as Outcome.Failure).error)
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun theHeaderIsReadByByteNotBySign() {
        assertEquals("ttf", FontImporter.extensionOf(byteArrayOf(0, 1, 0, 0)))
        assertEquals("otf", FontImporter.extensionOf("OTTO".toByteArray()))
        assertNull(FontImporter.extensionOf(byteArrayOf(-1, -1, -1, -1)))
        assertFalse(FontImporter.extensionOf(byteArrayOf(0, 1, 0, 1)) != null)
    }
}
