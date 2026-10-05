// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import android.view.KeyEvent
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.CustomFont
import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.launch.ResolvConf
import com.qtekfun.ultimateterminal.domain.session.SidebarMode
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.KeyboardType
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** What restoring a configuration does with the settings that came later (T16). */
class ConfigRestoreSettingsTest {
    @TempDir
    lateinit var dir: File
    private lateinit var device: Device

    @BeforeEach
    fun setUp() {
        device = Device(File(dir, "device"))
    }

    @AfterEach
    fun tearDown() = device.close()

    private fun restore(settings: SettingsDto) = runBlocking {
        val snapshot = sampleSnapshot().copy(settings = settings, distros = emptyList())
        val bytes = BackupBuilder("CONFIG").config(snapshot).build()
        device.restorer().restore(BytesSource(bytes)).value()
        device.settings.observe().first()
    }

    @Test
    fun aBackupFromBeforeTheseSettingsKeepsWhatTheDeviceHas() = runBlocking {
        device.settings.update {
            it.copy(
                prootCompatibilityMode = true,
                dnsFallbackServers = listOf("9.9.9.9"),
                extraKeys = ExtraKeysConfig(listOf(listOf("esc"))),
                appearance = TerminalAppearance(marginDp = 20)
            )
        }

        // The sample has none of the new fields, which is what an older backup looks like.
        val now = restore(sampleSettings())

        assertTrue(now.prootCompatibilityMode)
        assertEquals(listOf("9.9.9.9"), now.dnsFallbackServers)
        assertEquals(listOf(listOf("esc")), now.extraKeys.rows)
        assertEquals(20, now.appearance.marginDp)
    }

    @Test
    fun eachLaterSettingIsKeptOrReplacedOnItsOwn() = runBlocking {
        device.settings.update {
            it.copy(
                dnsFallbackServers = listOf("9.9.9.9"),
                extraKeys = ExtraKeysConfig(listOf(listOf("esc"))),
                appearance = TerminalAppearance(marginDp = 20)
            )
        }
        val appearance = AppearanceDto("jetbrains-mono", 1f, 0f, 6, "BLOCK", false, "SCHEME", 8)

        // Only the DNS servers come in the backup: the keys and the look stay.
        val onlyDns = restore(sampleSettings().copy(dnsFallbackServers = listOf("1.1.1.1")))
        assertEquals(listOf("1.1.1.1"), onlyDns.dnsFallbackServers)
        assertEquals(listOf(listOf("esc")), onlyDns.extraKeys.rows)
        assertEquals(20, onlyDns.appearance.marginDp)

        // Only the keys come: the servers and the look stay.
        val onlyKeys = restore(sampleSettings().copy(extraKeys = "tab ctrl\n"))
        assertEquals(listOf(listOf("tab", "ctrl")), onlyKeys.extraKeys.rows)
        assertEquals(listOf("1.1.1.1"), onlyKeys.dnsFallbackServers)
        assertEquals(20, onlyKeys.appearance.marginDp)

        // Only the look comes: the servers and the keys stay.
        val onlyLook = restore(sampleSettings().copy(appearance = appearance))
        assertEquals(6, onlyLook.appearance.marginDp)
        assertEquals(listOf("1.1.1.1"), onlyLook.dnsFallbackServers)
        assertEquals(listOf(listOf("tab", "ctrl")), onlyLook.extraKeys.rows)
    }

    private val mine = ShortcutMap.defaults()
        .unbind(KeyChord(KeyEvent.KEYCODE_T, ctrl = true, shift = true))
        .bind(KeyChord(KeyEvent.KEYCODE_K, ctrl = true, alt = true), AppShortcut.NewTab)

    private suspend fun keepOnDevice(map: ShortcutMap) =
        device.settings.update { it.copy(shortcuts = map) }

    @Test
    fun theShortcutsOfABackupReplaceTheOnesOfTheDevice() = runBlocking {
        keepOnDevice(ShortcutMap.defaults())

        val now = restore(
            sampleSettings().copy(shortcuts = ShortcutsDto(bindings = mine.serialize()))
        )

        assertEquals(mine, now.shortcuts)
    }

    @Test
    fun aBackupFromBeforeTheShortcutsKeepsTheOnesOfTheDevice() = runBlocking {
        keepOnDevice(mine)

        assertEquals(mine, restore(sampleSettings()).shortcuts)
    }

    @Test
    fun shortcutsInAFormatFromALaterAppAreNotGuessedAt() = runBlocking {
        keepOnDevice(mine)
        val later = ShortcutsDto(version = ShortcutsDto.VERSION + 1, bindings = "ctrl+k=copy\n")

        assertEquals(mine, restore(sampleSettings().copy(shortcuts = later)).shortcuts)
    }

    @Test
    fun aLineOfTheBackupThatMakesNoSenseIsDroppedAndTheRestIsKept() = runBlocking {
        keepOnDevice(ShortcutMap.defaults())
        val text = "ctrl+alt+k=new_tab\nctrl+alt+j=from_a_later_app\nt=new_tab\nghost+x=copy\n"

        val now = restore(sampleSettings().copy(shortcuts = ShortcutsDto(bindings = text)))

        assertEquals(
            mapOf(KeyChord(KeyEvent.KEYCODE_K, ctrl = true, alt = true) to AppShortcut.NewTab),
            now.shortcuts.all
        )
    }

    @Test
    fun shortcutsOfWhichNoLineIsUsableKeepTheOnesOfTheDevice() = runBlocking {
        keepOnDevice(mine)

        val now = restore(
            sampleSettings().copy(shortcuts = ShortcutsDto(bindings = "nothing here\n"))
        )

        assertEquals(mine, now.shortcuts)
    }

    @Test
    fun theSidebarModeOfABackupReplacesTheOneOfTheDevice() = runBlocking {
        val now = restore(
            sampleSettings().copy(sidebar = SidebarDto(mode = SidebarMode.ALWAYS_EXPANDED.name))
        )

        assertEquals(SidebarMode.ALWAYS_EXPANDED, now.sidebarMode)
    }

    @Test
    fun aBackupFromBeforeTheSidebarKeepsTheModeOfTheDevice() = runBlocking {
        device.settings.update { it.copy(sidebarMode = SidebarMode.ALWAYS_EXPANDED) }

        assertEquals(SidebarMode.ALWAYS_EXPANDED, restore(sampleSettings()).sidebarMode)
    }

    @Test
    fun aSidebarModeInAFormatFromALaterAppIsLeftAlone() = runBlocking {
        device.settings.update { it.copy(sidebarMode = SidebarMode.ALWAYS_EXPANDED) }
        val later = SidebarDto(version = SidebarDto.VERSION + 1, mode = "AUTO_COLLAPSE")

        assertEquals(
            SidebarMode.ALWAYS_EXPANDED,
            restore(sampleSettings().copy(sidebar = later)).sidebarMode
        )
    }

    @Test
    fun aSidebarModeThisAppDoesNotKnowBecomesTheDefault() = runBlocking {
        device.settings.update { it.copy(sidebarMode = SidebarMode.ALWAYS_EXPANDED) }

        val now = restore(sampleSettings().copy(sidebar = SidebarDto(mode = "from_the_future")))

        assertEquals(SidebarMode.DEFAULT, now.sidebarMode)
    }

    @Test
    fun theKeyboardTypeOfABackupReplacesTheOneOfTheDevice() = runBlocking {
        val now = restore(
            sampleSettings().copy(keyboard = KeyboardDto(type = KeyboardType.RAW.name))
        )

        assertEquals(KeyboardType.RAW, now.keyboardType)
    }

    @Test
    fun aBackupFromBeforeTheKeyboardTypeKeepsTheTypeOfTheDevice() = runBlocking {
        device.settings.update { it.copy(keyboardType = KeyboardType.COMPATIBLE) }

        assertEquals(KeyboardType.COMPATIBLE, restore(sampleSettings()).keyboardType)
    }

    @Test
    fun aKeyboardTypeInAFormatFromALaterAppIsLeftAlone() = runBlocking {
        device.settings.update { it.copy(keyboardType = KeyboardType.COMPATIBLE) }
        val later = KeyboardDto(version = KeyboardDto.VERSION + 1, type = "RAW")

        assertEquals(
            KeyboardType.COMPATIBLE,
            restore(sampleSettings().copy(keyboard = later)).keyboardType
        )
    }

    @Test
    fun aKeyboardTypeThisAppDoesNotKnowBecomesTheDefault() = runBlocking {
        device.settings.update { it.copy(keyboardType = KeyboardType.COMPATIBLE) }

        val now = restore(sampleSettings().copy(keyboard = KeyboardDto(type = "from_the_future")))

        assertEquals(KeyboardType.DEFAULT, now.keyboardType)
    }

    @Test
    fun aFontThisDeviceDoesNotHaveFallsBackToTheBundledOne() = runBlocking {
        val appearance = AppearanceDto(
            fontId = "custom-font-from-another-phone",
            lineSpacing = 1f,
            letterSpacing = 0f,
            marginDp = 6,
            cursorShape = "BAR",
            cursorBlink = false,
            chromeStyle = "SCHEME",
            cornerRadiusDp = 8
        )

        val now = restore(sampleSettings().copy(appearance = appearance))

        assertEquals(FontCatalog.BUNDLED_ID, now.appearance.fontId)
        assertEquals(CursorShape.BAR, now.appearance.cursorShape)
    }

    @Test
    fun aFontThatThisDeviceHasIsKept() = runBlocking {
        val mine = CustomFont("mine", "Mine", "mine.ttf")
        device.settings.update { it.copy(customFonts = listOf(mine)) }
        val appearance = AppearanceDto("mine", 1f, 0f, 6, "BLOCK", false, "SCHEME", 8)

        assertEquals(
            "mine",
            restore(sampleSettings().copy(appearance = appearance)).appearance.fontId
        )
    }

    @Test
    fun unknownNamesAndOutOfRangeNumbersFallBackToSafeValues() = runBlocking {
        val appearance =
            AppearanceDto("jetbrains-mono", 99f, -5f, 5_000, "TRIANGLE", true, "NEON", 999)

        val now = restore(sampleSettings().copy(appearance = appearance)).appearance

        assertEquals(CursorShape.BLOCK, now.cursorShape)
        assertEquals(TerminalAppearance().chromeStyle, now.chromeStyle)
        assertTrue(now.lineSpacing in TerminalAppearance.LINE_SPACING_RANGE)
        assertTrue(now.marginDp in TerminalAppearance.MARGIN_RANGE)
    }

    @Test
    fun theDnsServersAreCheckedNotTrusted() = runBlocking {
        val now =
            restore(sampleSettings().copy(dnsFallbackServers = listOf("9.9.9.9", "evil.example")))

        assertEquals(listOf("9.9.9.9"), now.dnsFallbackServers)
    }

    @Test
    fun anEmptyDnsListMeansTheBuiltInServers() = runBlocking {
        val now = restore(sampleSettings().copy(dnsFallbackServers = emptyList()))

        assertEquals(ResolvConf.FALLBACK_SERVERS, now.dnsFallbackServers)
    }

    @Test
    fun anExtraKeysRowWithNothingUsableFallsBackToTheDefaultRow() = runBlocking {
        val now = restore(sampleSettings().copy(extraKeys = "ghost nothing\n"))

        assertEquals(ExtraKeysConfig.default().rows, now.extraKeys.rows)
        assertFalse(now.extraKeys.rows.isEmpty())
    }
}
