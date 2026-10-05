// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import android.view.KeyEvent
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutConflict
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutEditingTest {
    private val defaults = ShortcutMap.defaults()
    private val ctrlAltK = KeyChord(KeyEvent.KEYCODE_K, ctrl = true, alt = true)

    @Test
    fun anActionListsItsCombinationsInOrderOfTheirNames() {
        assertEquals(
            listOf(
                KeyChord(KeyEvent.KEYCODE_INSERT, ctrl = true),
                KeyChord(KeyEvent.KEYCODE_C, ctrl = true, shift = true)
            ),
            ShortcutEditing.chordsOf(defaults, AppShortcut.Copy)
        )
    }

    @Test
    fun aFreeCombinationIsReadyAndTakesNothingFromAnyone() {
        val preview = ShortcutEditing.preview(defaults, AppShortcut.NewTab, "ctrl+alt+k")

        assertEquals(ShortcutPreview.Ready(ctrlAltK, null, null, alreadyBound = false), preview)
    }

    @Test
    fun aCombinationOfAnotherActionSaysWhichOneLosesIt() {
        val preview = ShortcutEditing.preview(defaults, AppShortcut.NewTab, "ctrl+shift+w")
            as ShortcutPreview.Ready

        assertEquals(AppShortcut.CloseTab, preview.replaced)
        assertFalse(preview.alreadyBound)
    }

    @Test
    fun theCostToAProgramInTheTerminalIsAWarningNotARefusal() {
        val preview = ShortcutEditing.preview(defaults, AppShortcut.NewTab, "ctrl+d")
            as ShortcutPreview.Ready

        assertEquals(
            ShortcutConflict.StealsControlKey(KeyChord(KeyEvent.KEYCODE_D, ctrl = true)),
            preview.conflict
        )
    }

    @Test
    fun theCombinationTheActionAlreadyHasChangesNothing() {
        val preview = ShortcutEditing.preview(defaults, AppShortcut.NewTab, "Ctrl+Shift+T")
            as ShortcutPreview.Ready

        assertTrue(preview.alreadyBound)
        assertNull(preview.replaced)
        assertEquals(defaults, ShortcutEditing.add(defaults, AppShortcut.NewTab, "ctrl+shift+t"))
    }

    @Test
    fun nothingTypedUnknownKeysAndPlainTypingAreNotReady() {
        assertEquals(
            ShortcutPreview.Empty,
            ShortcutEditing.preview(defaults, AppShortcut.Copy, "  ")
        )
        assertEquals(
            ShortcutPreview.Unreadable,
            ShortcutEditing.preview(defaults, AppShortcut.Copy, "ctrl+banana")
        )
        assertEquals(
            ShortcutPreview.NeedsModifier,
            ShortcutEditing.preview(defaults, AppShortcut.Copy, "shift+k")
        )
        assertEquals(
            ShortcutPreview.NeedsModifier,
            ShortcutEditing.preview(defaults, AppShortcut.Copy, "k")
        )
    }

    @Test
    fun addingGivesTheActionTheCombinationAndTakesItFromTheOldOwner() {
        val added = ShortcutEditing.add(defaults, AppShortcut.NewTab, "ctrl+shift+w")

        assertEquals(
            AppShortcut.NewTab,
            added.all[KeyChord(KeyEvent.KEYCODE_W, ctrl = true, shift = true)]
        )
        assertTrue(ShortcutEditing.chordsOf(added, AppShortcut.CloseTab).isEmpty())
    }

    @Test
    fun addingSomethingThatCannotBeAddedLeavesTheMapAlone() {
        assertEquals(defaults, ShortcutEditing.add(defaults, AppShortcut.NewTab, "k"))
        assertEquals(defaults, ShortcutEditing.add(defaults, AppShortcut.NewTab, "?"))
    }

    @Test
    fun removingFreesTheCombination() {
        val chord = KeyChord(KeyEvent.KEYCODE_T, ctrl = true, shift = true)

        val removed = ShortcutEditing.remove(defaults, chord)

        assertNull(removed.all[chord])
        assertEquals(defaults.all.size - 1, removed.all.size)
    }

    @Test
    fun aKeyPressBecomesTheTextOfItsCombination() {
        assertEquals(
            "ctrl+alt+k",
            ShortcutEditing.captured(KeyEvent.KEYCODE_K, ctrl = true, alt = true, shift = false)
        )
        assertEquals(
            "ctrl+shift+left",
            ShortcutEditing.captured(KeyEvent.KEYCODE_DPAD_LEFT, true, false, true)
        )
    }

    @Test
    fun aModifierAloneOrAKeyWithoutANameIsNotACombinationYet() {
        for (modifier in listOf(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT
        )) {
            assertNull(ShortcutEditing.captured(modifier, ctrl = true, alt = false, shift = false))
        }
        assertNull(
            ShortcutEditing.captured(
                KeyEvent.KEYCODE_VOLUME_UP,
                ctrl = true,
                alt = false,
                shift = false
            )
        )
    }

    @Test
    fun theEditorListsEveryActionEvenWhenItHasNoCombinationLeft() {
        val emptied = defaults.all.filterValues { it == AppShortcut.ToggleZoom }.keys
            .fold(defaults) { map, chord -> map.unbind(chord) }

        val rows = ShortcutDisplay.allRows(emptied)

        assertEquals(ShortcutDisplay.rows(defaults).size, rows.size)
        assertEquals(
            emptyList<String>(),
            rows.first {
                it.shortcut == AppShortcut.ToggleZoom
            }.chords
        )
        assertFalse(ShortcutDisplay.rows(emptied).any { it.shortcut == AppShortcut.ToggleZoom })
    }

    @Test
    fun theTabNumbersAreOneRowEvenIfNoneIsBound() {
        val none = AppShortcut.SelectTab(1)
        val unbound = (1..AppShortcut.MAX_DIRECT_TAB).fold(defaults) { map, n ->
            map.unbind(KeyChord(KeyEvent.KEYCODE_0 + n, alt = true))
        }

        val row = ShortcutDisplay.allRows(unbound).first { it.shortcut == none }

        assertTrue(row.chords.isEmpty())
    }
}
