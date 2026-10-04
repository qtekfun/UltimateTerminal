// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutConflictsTest {
    private fun chord(
        code: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false
    ) = KeyChord(code, ctrl, alt, shift)

    @Test
    fun ctrlPlusALetterAloneTakesAControlKeyTheShellNeeds() {
        val chord = chord(KeyEvent.KEYCODE_C, ctrl = true)

        assertEquals(ShortcutConflict.StealsControlKey(chord), chord.terminalConflict())
    }

    @Test
    fun altPlusALetterOrDigitTakesReadlinesMeta() {
        val digit = chord(KeyEvent.KEYCODE_3, alt = true)
        val letter = chord(KeyEvent.KEYCODE_B, alt = true, shift = true)

        assertEquals(ShortcutConflict.StealsReadlineMeta(digit), digit.terminalConflict())
        assertEquals(ShortcutConflict.StealsReadlineMeta(letter), letter.terminalConflict())
    }

    @Test
    fun chordsThatRarelyClashAreNotFlagged() {
        assertNull(chord(KeyEvent.KEYCODE_C, ctrl = true, shift = true).terminalConflict())
        assertNull(chord(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true, alt = true).terminalConflict())
        assertNull(chord(KeyEvent.KEYCODE_TAB, ctrl = true).terminalConflict())
        assertNull(chord(KeyEvent.KEYCODE_TAB, alt = true).terminalConflict())
        assertNull(chord(KeyEvent.KEYCODE_C, ctrl = true, alt = true).terminalConflict())
    }

    @Test
    fun theOnlyDefaultsThatClashAreTheAltDigitTabKeys() {
        val conflicts = ShortcutMap.defaults().conflicts()

        assertEquals(AppShortcut.MAX_DIRECT_TAB, conflicts.size)
        assertTrue(conflicts.all { it is ShortcutConflict.StealsReadlineMeta })
    }

    @Test
    fun conflictsComeInAStableOrder() {
        val map = ShortcutMap.defaults()
            .bind(chord(KeyEvent.KEYCODE_D, ctrl = true), AppShortcut.ClosePane)
            .bind(chord(KeyEvent.KEYCODE_A, ctrl = true), AppShortcut.NewTab)

        val first = map.conflicts().filterIsInstance<ShortcutConflict.StealsControlKey>()

        assertEquals(listOf("ctrl+a", "ctrl+d"), first.map { it.chord.format() })
    }

    @Test
    fun bindingReportsWhatItReplacedAndWhatItCosts() {
        val defaults = ShortcutMap.defaults()
        val newTab = chord(KeyEvent.KEYCODE_T, ctrl = true, shift = true)

        val replaced = defaults.bindChecked(newTab, AppShortcut.CloseTab) as BindResult.Bound
        val same = defaults.bindChecked(newTab, AppShortcut.NewTab) as BindResult.Bound
        val costly = defaults.bindChecked(chord(KeyEvent.KEYCODE_C, ctrl = true), AppShortcut.Copy)
            as BindResult.Bound

        assertEquals(AppShortcut.NewTab, replaced.replaced)
        assertEquals(AppShortcut.CloseTab, replaced.map.all[newTab])
        assertNull(replaced.conflict)
        assertNull(same.replaced)
        assertNull(costly.replaced)
        assertTrue(costly.conflict is ShortcutConflict.StealsControlKey)
    }

    @Test
    fun aChordThatWouldStealOrdinaryTypingIsRefusedWithoutThrowing() {
        val result = ShortcutMap.defaults().bindChecked(
            chord(KeyEvent.KEYCODE_A),
            AppShortcut.NewTab
        )

        assertEquals(BindResult.Refused(BindRefusal.NEEDS_CTRL_OR_ALT), result)
    }

    @Test
    fun textThatGivesAChordToTwoActionsReportsTheOneThatWon() {
        val text = """
            # my shortcuts
            ctrl+shift+t=new_tab
            ctrl+shift+t=close_tab
            ctrl+shift+q=close_pane
            ctrl+shift+q=close_pane
            bogus
        """.trimIndent()

        val analysis = ShortcutText.analyze(text)

        val chord = chord(KeyEvent.KEYCODE_T, ctrl = true, shift = true)
        assertEquals(
            listOf(
                ShortcutConflict.DuplicateInText(chord, AppShortcut.CloseTab, AppShortcut.NewTab)
            ),
            analysis.duplicates
        )
        assertEquals(AppShortcut.CloseTab, analysis.map.all[chord])
        assertEquals(listOf("bogus"), analysis.rejected.map { it.line })
    }

    @Test
    fun theBroadcastAndLayoutShortcutsHaveDefaultsThatSurviveStorage() {
        val defaults = ShortcutMap.defaults()
        val restored = ShortcutMap.parse(defaults.serialize()).map

        for ((code, shortcut) in listOf(
            KeyEvent.KEYCODE_B to AppShortcut.ToggleBroadcast,
            KeyEvent.KEYCODE_S to AppShortcut.SaveLayout,
            KeyEvent.KEYCODE_L to AppShortcut.OpenLayouts
        )) {
            val chord = chord(code, ctrl = true, shift = true)
            assertEquals(shortcut, defaults.all[chord])
            assertEquals(shortcut, restored.all[chord])
            assertEquals(shortcut, AppShortcut.fromId(shortcut.id))
        }
        assertEquals(defaults.all, restored.all)
    }
}
