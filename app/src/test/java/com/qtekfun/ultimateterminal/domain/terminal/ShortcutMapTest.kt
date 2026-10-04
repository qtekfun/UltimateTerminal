// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutMapTest {
    private val defaults = ShortcutMap.defaults()

    private fun key(
        code: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false
    ) = KeyInput(code, 0, ctrl = ctrl, alt = alt, shift = shift)

    @Test
    fun theSpecifiedDefaultsAreBound() {
        assertEquals(
            AppShortcut.NewTab,
            defaults.match(key(KeyEvent.KEYCODE_T, ctrl = true, shift = true))
        )
        assertEquals(
            AppShortcut.SelectTab(3),
            defaults.match(key(KeyEvent.KEYCODE_3, alt = true))
        )
        assertEquals(
            AppShortcut.SelectTab(9),
            defaults.match(key(KeyEvent.KEYCODE_9, alt = true))
        )
    }

    @Test
    fun copyPasteAndZoomHaveSeveralChords() {
        assertEquals(AppShortcut.Copy, defaults.match(key(KeyEvent.KEYCODE_INSERT, ctrl = true)))
        assertEquals(AppShortcut.Paste, defaults.match(key(KeyEvent.KEYCODE_INSERT, shift = true)))
        assertEquals(
            AppShortcut.Paste,
            defaults.match(key(KeyEvent.KEYCODE_V, ctrl = true, shift = true))
        )
        assertEquals(
            AppShortcut.ZoomIn,
            defaults.match(key(KeyEvent.KEYCODE_EQUALS, ctrl = true, shift = true))
        )
        assertEquals(
            AppShortcut.ZoomOut,
            defaults.match(key(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, ctrl = true))
        )
        assertEquals(
            AppShortcut.ZoomReset,
            defaults.match(key(KeyEvent.KEYCODE_0, ctrl = true, shift = true))
        )
        assertEquals(
            AppShortcut.PreviousTab,
            defaults.match(key(KeyEvent.KEYCODE_TAB, ctrl = true, shift = true))
        )
        assertEquals(AppShortcut.NextTab, defaults.match(key(KeyEvent.KEYCODE_TAB, ctrl = true)))
        assertEquals(
            AppShortcut.CloseTab,
            defaults.match(key(KeyEvent.KEYCODE_W, ctrl = true, shift = true))
        )
    }

    @Test
    fun matchingIsExactOnModifiers() {
        assertNull(defaults.match(key(KeyEvent.KEYCODE_T, ctrl = true)))
        assertNull(defaults.match(key(KeyEvent.KEYCODE_3, alt = true, shift = true)))
        assertNull(defaults.match(key(KeyEvent.KEYCODE_3)))
        assertNull(defaults.match(key(KeyEvent.KEYCODE_C, ctrl = true)))
    }

    @Test
    fun plainTypingIsNeverAShortcut() {
        assertTrue(defaults.all.keys.none { !it.isUsable })
        assertTrue(
            defaults.all.keys.none {
                !it.ctrl && !it.alt && it.keyCode != KeyEvent.KEYCODE_INSERT
            }
        )
    }

    @Test
    fun bindReplacesAndUnbindRemoves() {
        val chord = KeyChord(KeyEvent.KEYCODE_N, ctrl = true, alt = true)
        val bound = defaults.bind(chord, AppShortcut.NewTab)
        val input = key(KeyEvent.KEYCODE_N, ctrl = true, alt = true)
        assertEquals(AppShortcut.NewTab, bound.match(input))
        assertNull(bound.unbind(chord).match(input))
        assertNull(defaults.match(input))
    }

    @Test
    fun bindingAChordWithoutCtrlOrAltFails() {
        assertThrows(IllegalArgumentException::class.java) {
            defaults.bind(KeyChord(KeyEvent.KEYCODE_A, shift = true), AppShortcut.Copy)
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaults.bind(KeyChord(KeyEvent.KEYCODE_A), AppShortcut.Copy)
        }
    }

    @Test
    fun selectTabNumbersAreRangeChecked() {
        assertThrows(IllegalArgumentException::class.java) { AppShortcut.SelectTab(0) }
        assertThrows(IllegalArgumentException::class.java) { AppShortcut.SelectTab(10) }
        assertEquals(AppShortcut.SelectTab(4), AppShortcut.fromId("select_tab_4"))
        assertNull(AppShortcut.fromId("select_tab_10"))
        assertNull(AppShortcut.fromId("nope"))
    }

    @Test
    fun chordsFormatAndParse() {
        val chord = KeyChord(KeyEvent.KEYCODE_T, ctrl = true, shift = true)
        assertEquals("ctrl+shift+t", chord.format())
        assertEquals(chord, KeyChord.parse("Ctrl+Shift+T"))
        assertEquals(KeyChord(KeyEvent.KEYCODE_F5, alt = true), KeyChord.parse("alt+f5"))
        assertEquals("alt+page_up", KeyChord(KeyEvent.KEYCODE_PAGE_UP, alt = true).format())
        assertNull(KeyChord(KeyEvent.KEYCODE_ENTER, ctrl = true).format())
    }

    @Test
    fun badChordsDoNotParse() {
        assertNull(KeyChord.parse("ctrl+"))
        assertNull(KeyChord.parse("hyper+t"))
        assertNull(KeyChord.parse("ctrl+shift+bogus"))
        assertNull(KeyChord.parse(""))
        assertNull(KeyChord.parse("ctrl+ctrl+t"))
    }

    @Test
    fun theMapRoundTripsThroughText() {
        val parsed = ShortcutMap.parse(defaults.serialize())
        assertTrue(parsed.rejected.isEmpty())
        assertEquals(defaults.all, parsed.map.all)
    }

    @Test
    fun parseReportsEachBadLineAndKeepsTheGoodOnes() {
        val text = """
            # a comment
            ctrl+shift+t=new_tab
            ctrl+shift+q
            ctrl+bogus+t=copy
            shift+a=copy
            alt+x=teleport

            alt+1=select_tab_1
        """.trimIndent()
        val parsed = ShortcutMap.parse(text)
        assertEquals(2, parsed.map.all.size)
        assertEquals(
            listOf("missing =", "unknown key", "needs Ctrl or Alt", "unknown shortcut"),
            parsed.rejected.map { it.reason }
        )
    }
}
