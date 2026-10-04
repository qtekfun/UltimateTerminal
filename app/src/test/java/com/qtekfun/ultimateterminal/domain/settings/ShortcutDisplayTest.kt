// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import android.view.KeyEvent
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutDisplayTest {
    private val defaults = ShortcutDisplay.rows(ShortcutMap.defaults())

    private fun chordsOf(shortcut: AppShortcut) = defaults.first { it.shortcut == shortcut }.chords

    @Test
    fun aChordReadsWithCapitalizedModifiersAndKey() {
        assertEquals(
            "Ctrl+Shift+T",
            ShortcutDisplay.pretty(KeyChord(KeyEvent.KEYCODE_T, ctrl = true, shift = true))
        )
        assertEquals(
            "Ctrl+Tab",
            ShortcutDisplay.pretty(KeyChord(KeyEvent.KEYCODE_TAB, ctrl = true))
        )
        assertEquals(
            "Shift+Insert",
            ShortcutDisplay.pretty(KeyChord(KeyEvent.KEYCODE_INSERT, shift = true))
        )
        assertEquals(
            "Ctrl+Alt+←",
            ShortcutDisplay.pretty(KeyChord(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true, alt = true))
        )
        assertEquals(
            "Ctrl+Num +",
            ShortcutDisplay.pretty(KeyChord(KeyEvent.KEYCODE_NUMPAD_ADD, ctrl = true))
        )
        assertEquals("Alt+F5", ShortcutDisplay.pretty(KeyChord(KeyEvent.KEYCODE_F5, alt = true)))
    }

    @Test
    fun aKeyWithNoNameHasNoText() {
        assertNull(ShortcutDisplay.pretty(KeyChord(KeyEvent.KEYCODE_BUTTON_A, ctrl = true)))
    }

    @Test
    fun theDefaultsAreListedInTheFixedOrderWithOneLineForTheTabNumbers() {
        val shortcuts = defaults.map { it.shortcut }

        assertEquals(AppShortcut.NewTab, shortcuts.first())
        assertEquals(AppShortcut.ZoomReset, shortcuts.last())
        assertEquals(1, shortcuts.count { it is AppShortcut.SelectTab })
        assertEquals(listOf("Alt+1–9"), chordsOf(AppShortcut.SelectTab(1)))
    }

    @Test
    fun aShortcutWithTwoChordsListsBothSorted() {
        assertEquals(listOf("Ctrl+Insert", "Ctrl+Shift+C"), chordsOf(AppShortcut.Copy))
        assertEquals(listOf("Ctrl+Shift+T"), chordsOf(AppShortcut.NewTab))
    }

    @Test
    fun everyDefaultBindingAppearsSomewhere() {
        val shown = defaults.sumOf {
            if (it.shortcut is AppShortcut.SelectTab) AppShortcut.MAX_DIRECT_TAB else it.chords.size
        }

        assertEquals(ShortcutMap.defaults().all.size, shown)
    }

    @Test
    fun everyShortcutWithADefaultKeyIsListed() {
        // A shortcut added to AppShortcut and bound by default, but not put in the list, would work
        // and never show in Settings (it happened with the three that T12b added).
        val listed = defaults.map { it.shortcut.id }.toSet()
        val bound = ShortcutMap.defaults().all.values
            .map { if (it is AppShortcut.SelectTab) AppShortcut.SelectTab(1) else it }
            .toSet()

        for (shortcut in bound) {
            assertTrue(
                shortcut.id in listed,
                "\${shortcut.id} is missing from the list in Settings"
            )
        }
    }

    @Test
    fun aMapWithoutBindingsHasNoRowsAndAnUnboundShortcutIsLeftOut() {
        assertTrue(ShortcutDisplay.rows(ShortcutMap.parse("").map).isEmpty())
        val withoutPaste = ShortcutMap.defaults()
            .unbind(KeyChord(KeyEvent.KEYCODE_V, ctrl = true, shift = true))
            .unbind(KeyChord(KeyEvent.KEYCODE_INSERT, shift = true))

        assertTrue(ShortcutDisplay.rows(withoutPaste).none { it.shortcut == AppShortcut.Paste })
    }

    @Test
    fun tabNumbersThatDoNotShareModifiersAreListedOneByOne() {
        val mixed = ShortcutMap.parse("alt+1=select_tab_1\nctrl+2=select_tab_2\n").map

        val row = ShortcutDisplay.rows(mixed).single()

        assertEquals(listOf("Alt+1", "Ctrl+2"), row.chords)
    }
}
