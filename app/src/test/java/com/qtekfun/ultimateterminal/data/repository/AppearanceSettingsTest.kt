// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.domain.appearance.ChromeStyle
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.CustomFont
import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppearanceSettingsTest {
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

    @Test
    fun aFreshInstallUsesTheDefaultAppearanceAndNoFonts() = runTest {
        val stored = settings.observe().first()

        assertEquals(TerminalAppearance(), stored.appearance)
        assertEquals(emptyList<CustomFont>(), stored.customFonts)
    }

    @Test
    fun theWholeAppearanceAndTheFontsAreStored() = runTest {
        val font = CustomFont("font-fira-1", "Fira Code", "font-fira-1.ttf")
        val mine = TerminalAppearance(
            fontId = font.id,
            lineSpacing = 1.25f,
            letterSpacing = 0.05f,
            marginDp = 12,
            cursorShape = CursorShape.BAR,
            cursorBlink = true,
            chromeStyle = ChromeStyle.SYSTEM,
            cornerRadiusDp = 4
        )

        settings.update { it.copy(appearance = mine, customFonts = listOf(font)) }

        val stored = settings.observe().first()
        assertEquals(mine, stored.appearance)
        assertEquals(listOf(font), stored.customFonts)
    }

    @Test
    fun numbersOutOfRangeAreClampedWhenSavedAndWhenRead() = runTest {
        settings.update {
            it.copy(appearance = TerminalAppearance(marginDp = 500, lineSpacing = 9f))
        }
        assertEquals(
            TerminalAppearance.MARGIN_RANGE.last,
            settings.observe().first().appearance.marginDp
        )

        store("appearance_corner_dp", "-9")
        store("appearance_letter_spacing", "5")
        val read = settings.observe().first().appearance
        assertEquals(TerminalAppearance.CORNER_RANGE.first, read.cornerRadiusDp)
        assertEquals(TerminalAppearance.LETTER_SPACING_RANGE.endInclusive, read.letterSpacing)
    }

    @Test
    fun unreadableValuesKeepTheirDefaults() = runTest {
        store("appearance_line_spacing", "wide")
        store("appearance_margin_dp", "many")
        store("appearance_cursor_shape", "STAR")
        store("appearance_cursor_blink", "maybe")
        store("appearance_chrome_style", "")
        store("appearance_font_id", "  ")
        store("custom_fonts", "not json")

        val stored = settings.observe().first()

        assertEquals(TerminalAppearance(), stored.appearance)
        assertEquals(emptyList<CustomFont>(), stored.customFonts)
    }

    @Test
    fun theKeysAreTheOnesTheBackupWillSerialize() {
        // These names are part of the backup format (T15): renaming one breaks old backups.
        assertEquals("appearance_font_id", SettingKeys.FONT_ID)
        assertEquals("appearance_line_spacing", SettingKeys.LINE_SPACING)
        assertEquals("appearance_letter_spacing", SettingKeys.LETTER_SPACING)
        assertEquals("appearance_margin_dp", SettingKeys.MARGIN_DP)
        assertEquals("appearance_cursor_shape", SettingKeys.CURSOR_SHAPE)
        assertEquals("appearance_cursor_blink", SettingKeys.CURSOR_BLINK)
        assertEquals("appearance_chrome_style", SettingKeys.CHROME_STYLE)
        assertEquals("appearance_corner_dp", SettingKeys.CORNER_DP)
        assertEquals("custom_fonts", SettingKeys.CUSTOM_FONTS)
        assertEquals(FontCatalog.BUNDLED_ID, TerminalAppearance().fontId)
    }
}
