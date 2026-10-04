// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.data.local.runDatabaseTest
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThemeSettingsTest {
    private lateinit var db: UltimateTerminalDatabase
    private lateinit var settings: RoomSettingsRepository

    @BeforeEach
    fun setUp() {
        db = inMemoryDatabase()
        settings = RoomSettingsRepository(db.settingDao())
    }

    @AfterEach
    fun tearDown() = db.close()

    private suspend fun store(key: String, value: String) =
        db.settingDao().upsert(listOf(SettingEntity(key, value)))

    private fun imported(name: String) =
        SchemeCodec.decode(SchemeCodec.encode(BuiltInSchemes.nord.copy(name = name))).getOrNull()!!

    @Test
    fun aFreshInstallUsesTheDefaultSchemeAndFontSize() = runDatabaseTest {
        val stored = settings.observe().first()
        assertEquals(BuiltInSchemes.DEFAULT_ID, stored.terminalSchemeId)
        assertEquals(FontZoom.DEFAULT_SP, stored.terminalFontSizeSp)
        assertEquals(emptyList<Any>(), stored.customSchemes)
    }

    @Test
    fun theSchemeTheFontSizeAndTheImportedSchemesAreStored() = runDatabaseTest {
        val mine = imported("Mine")
        settings.update {
            it.copy(
                terminalSchemeId = mine.id,
                terminalFontSizeSp = 17.5f,
                customSchemes = listOf(mine)
            )
        }

        val stored = settings.observe().first()
        assertEquals("custom-mine", stored.terminalSchemeId)
        assertEquals(17.5f, stored.terminalFontSizeSp)
        assertEquals(listOf(mine), stored.customSchemes)
        assertEquals(mine, SchemeCatalog.resolve(stored.terminalSchemeId, stored.customSchemes))
    }

    @Test
    fun aFontSizeOutOfRangeIsClampedWhenSavedAndWhenRead() = runDatabaseTest {
        settings.update { it.copy(terminalFontSizeSp = 900f) }
        assertEquals(FontZoom.MAX_SP, settings.observe().first().terminalFontSizeSp)

        store("terminal_font_size_sp", "0.5")
        assertEquals(FontZoom.MIN_SP, settings.observe().first().terminalFontSizeSp)
    }

    @Test
    fun damagedValuesFallBackToTheDefaults() = runDatabaseTest {
        store("terminal_font_size_sp", "big")
        store("terminal_scheme", "  ")
        store("custom_schemes", "{broken")

        val stored = settings.observe().first()
        assertEquals(AppSettings().terminalFontSizeSp, stored.terminalFontSizeSp)
        assertEquals(AppSettings().terminalSchemeId, stored.terminalSchemeId)
        assertEquals(emptyList<Any>(), stored.customSchemes)
    }

    @Test
    fun aFontSizeThatIsNotFiniteIsIgnored() = runDatabaseTest {
        store("terminal_font_size_sp", "NaN")
        assertEquals(FontZoom.DEFAULT_SP, settings.observe().first().terminalFontSizeSp)
        store("terminal_font_size_sp", "Infinity")
        assertEquals(FontZoom.DEFAULT_SP, settings.observe().first().terminalFontSizeSp)
    }

    @Test
    fun onlyTheAllowedNumberOfImportedSchemesIsRead() = runDatabaseTest {
        val many = (1..SchemeCatalog.MAX_CUSTOM_SCHEMES + 5).map { imported("Scheme $it") }
        store("custom_schemes", SchemeCodec.encodeList(many))

        assertEquals(
            SchemeCatalog.MAX_CUSTOM_SCHEMES,
            settings.observe().first().customSchemes.size
        )
    }

    @Test
    fun theSettingsKeysAreStableBecauseTheyArePartOfTheBackup() {
        assertEquals("terminal_scheme", SettingKeys.TERMINAL_SCHEME)
        assertEquals("terminal_font_size_sp", SettingKeys.TERMINAL_FONT_SIZE_SP)
        assertEquals("custom_schemes", SettingKeys.CUSTOM_SCHEMES)
    }
}
