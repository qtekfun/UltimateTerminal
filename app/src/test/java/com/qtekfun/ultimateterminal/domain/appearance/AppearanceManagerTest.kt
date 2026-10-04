// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import com.qtekfun.ultimateterminal.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppearanceManagerTest {
    private class Files : FontFileStore {
        val written = mutableMapOf<String, ByteArray>()
        var probe: FontProbe = Good

        override suspend fun write(fileName: String, bytes: ByteArray): Boolean {
            written[fileName] = bytes
            return true
        }

        override suspend fun delete(fileName: String) {
            written.remove(fileName)
        }

        override fun probe(fileName: String) = if (fileName in written) probe else null
    }

    private object Good : FontProbe {
        override fun loads() = true
        override fun hasGlyphs(text: String) = true
        override fun advance(text: String) = 10f
    }

    private val ttf = byteArrayOf(0, 1, 0, 0, 5, 5)
    private val repository = FakeSettingsRepository()
    private val files = Files()
    private val manager = AppearanceManager(repository, FontImporter(files) { "x1" }, files)

    private suspend fun settings(): AppSettings = repository.observe().first()

    private fun mine(name: String) =
        SchemeEditor.blank(name, BuiltInSchemes.dracula, BuiltInSchemes.all)

    @Test
    fun anImportedFontIsStoredAndNotSelected() = runTest {
        val font = (manager.importFont(ttf, "Fira_Code.ttf") as Outcome.Success).value

        assertEquals(listOf(font), settings().customFonts)
        assertEquals(FontCatalog.BUNDLED_ID, settings().appearance.fontId)
        assertTrue(font.fileName in files.written)
    }

    @Test
    fun aRejectedFontChangesNothing() = runTest {
        val result = manager.importFont("nope".toByteArray(), "x.ttf")

        assertTrue(result is Outcome.Failure)
        assertTrue(settings().customFonts.isEmpty())
        assertTrue(files.written.isEmpty())
    }

    @Test
    fun theSameFontCannotBeImportedTwice() = runTest {
        manager.importFont(ttf, "Hack.ttf")

        val again = manager.importFont(ttf, "hack.ttf")

        assertEquals(DomainError.NameTaken("hack"), (again as Outcome.Failure).error)
        assertEquals(1, settings().customFonts.size)
    }

    @Test
    fun deletingTheFontInUseFallsBackToTheBundledOne() = runTest {
        val font = (manager.importFont(ttf, "Hack.ttf") as Outcome.Success).value
        repository.update { it.copy(appearance = it.appearance.copy(fontId = font.id)) }

        manager.deleteFont(font.id)

        assertTrue(settings().customFonts.isEmpty())
        assertEquals(FontCatalog.BUNDLED_ID, settings().appearance.fontId)
        assertFalse(font.fileName in files.written)
    }

    @Test
    fun deletingAnotherFontKeepsTheSelection() = runTest {
        val first = (manager.importFont(ttf, "One.ttf") as Outcome.Success).value
        val second = (
            FontImporter(files) {
                "x2"
            }.import(ttf, "Two.ttf", listOf(first)) as Outcome.Success
            ).value.font
        repository.update {
            it.copy(
                customFonts = listOf(first, second),
                appearance = it.appearance.copy(fontId = second.id)
            )
        }

        manager.deleteFont(first.id)

        assertEquals(listOf(second), settings().customFonts)
        assertEquals(second.id, settings().appearance.fontId)
    }

    @Test
    fun deletingAFontThatIsNotThereDoesNothing() = runTest {
        manager.deleteFont("font-gone")

        assertEquals(AppSettings(), settings())
    }

    @Test
    fun aSavedSchemeIsStored() = runTest {
        val scheme = mine("Mine")

        assertEquals(Outcome.Success(Unit), manager.saveScheme(null, scheme))

        assertEquals(listOf(scheme), settings().customSchemes)
    }

    @Test
    fun aNameInUseIsReportedAndNothingIsStored() = runTest {
        val clash = BuiltInSchemes.nord.copy(builtIn = false)

        val result = manager.saveScheme(null, clash)

        assertEquals(DomainError.NameTaken("Nord"), (result as Outcome.Failure).error)
        assertTrue(settings().customSchemes.isEmpty())
    }

    @Test
    fun editingTheSchemeInUseKeepsItSelectedEvenIfItIsRenamed() = runTest {
        val scheme = mine("Old")
        manager.saveScheme(null, scheme)
        repository.update { it.copy(terminalSchemeId = scheme.id) }
        val renamed = (SchemeEditor.renamed(scheme, "New") as Outcome.Success).value

        manager.saveScheme(scheme.id, renamed)

        assertEquals(listOf("New"), settings().customSchemes.map { it.name })
        assertEquals("custom-new", settings().terminalSchemeId)
    }

    @Test
    fun editingAnotherSchemeDoesNotChangeTheSelection() = runTest {
        val scheme = mine("Old")
        manager.saveScheme(null, scheme)

        manager.saveScheme(scheme.id, scheme.copy(cursor = 0xFF00FF00.toInt()))

        assertEquals(BuiltInSchemes.DEFAULT_ID, settings().terminalSchemeId)
    }

    @Test
    fun deletingTheSchemeInUseSelectsTheDefault() = runTest {
        val scheme = mine("Gone")
        manager.saveScheme(null, scheme)
        repository.update { it.copy(terminalSchemeId = scheme.id) }

        manager.deleteScheme(scheme.id)

        assertTrue(settings().customSchemes.isEmpty())
        assertEquals(BuiltInSchemes.DEFAULT_ID, settings().terminalSchemeId)
    }

    @Test
    fun deletingAnotherSchemeKeepsTheSelection() = runTest {
        val keep = mine("Keep")
        val drop = mine("Drop")
        manager.saveScheme(null, keep)
        manager.saveScheme(null, drop)
        repository.update { it.copy(terminalSchemeId = keep.id) }

        manager.deleteScheme(drop.id)

        assertEquals(keep.id, settings().terminalSchemeId)
    }

    @Test
    fun anImportedSchemeIsStoredAndSelected() = runTest {
        val text = SchemeCodec.encode(BuiltInSchemes.nord.copy(name = "From a file"))

        val result = manager.importScheme(text)

        assertEquals("custom-from-a-file", (result as Outcome.Success).value.id)
        assertEquals("custom-from-a-file", settings().terminalSchemeId)
        assertEquals(1, settings().customSchemes.size)
    }

    @Test
    fun aFileThatIsNotASchemeIsRefused() = runTest {
        assertTrue(manager.importScheme("{}") is Outcome.Failure)
        assertTrue(settings().customSchemes.isEmpty())
    }

    @Test
    fun aSchemeWhoseNameIsTakenIsRefusedAndNotSelected() = runTest {
        val text = SchemeCodec.encode(BuiltInSchemes.nord)

        val result = manager.importScheme(text)

        assertEquals(DomainError.NameTaken("Nord"), (result as Outcome.Failure).error)
        assertEquals(BuiltInSchemes.DEFAULT_ID, settings().terminalSchemeId)
    }
}
