// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtraKeysEditingTest {
    private val two = ExtraKeysConfig(listOf(listOf("esc", "tab"), listOf("up")))

    @Test
    fun theUnusedKeysAreTheCatalogMinusTheOnesInTheRows() {
        val unused = two.unusedKeys().map { it.id }

        assertFalse("esc" in unused)
        assertFalse("up" in unused)
        assertTrue("ctrl" in unused)
        assertEquals(ExtraKeyCatalog.all.size - 3, unused.size)
    }

    @Test
    fun aKeyIsAddedToTheEndOfARow() {
        assertEquals(
            listOf(listOf("esc", "tab", "ctrl"), listOf("up")),
            two.withKey(0, "ctrl").rows
        )
    }

    @Test
    fun aRowEqualToTheNumberOfRowsStartsANewOne() {
        assertEquals(
            listOf(listOf("esc", "tab"), listOf("up"), listOf("ctrl")),
            two.withKey(2, "ctrl").rows
        )
    }

    @Test
    fun anUnknownOrAlreadyUsedKeyChangesNothing() {
        assertSame(two, two.withKey(0, "ghost"))
        assertSame(two, two.withKey(1, "esc"))
    }

    @Test
    fun aFullRowAndTooManyRowsChangeNothing() {
        val full = ExtraKeysConfig(
            listOf(
                listOf("esc", "tab", "ctrl", "alt", "up", "down", "left", "right"),
                listOf("home"),
                listOf("end")
            )
        )

        assertSame(full, full.withKey(0, "pgup"))
        assertSame(full, full.withKey(3, "pgup"))
        assertSame(two, two.withKey(5, "ctrl"))
        assertSame(two, two.withKey(-1, "ctrl"))
    }

    @Test
    fun removingTheLastKeyOfARowRemovesTheRow() {
        assertEquals(listOf(listOf("esc", "tab")), two.withoutKey(1, 0).rows)
        assertEquals(listOf(listOf("tab"), listOf("up")), two.withoutKey(0, 0).rows)
    }

    @Test
    fun removingWhereThereIsNothingChangesNothing() {
        assertSame(two, two.withoutKey(2, 0))
        assertSame(two, two.withoutKey(0, 9))
        assertSame(two, two.withoutKey(-1, 0))
    }

    @Test
    fun aKeyMovesWithinItsRowAndIsClamped() {
        val row = ExtraKeysConfig(listOf(listOf("esc", "tab", "ctrl")))

        assertEquals(listOf("tab", "ctrl", "esc"), row.withKeyMoved(0, 0, 2).rows[0])
        assertEquals(listOf("ctrl", "esc", "tab"), row.withKeyMoved(0, 2, 0).rows[0])
        assertEquals(listOf("tab", "ctrl", "esc"), row.withKeyMoved(0, 0, 99).rows[0])
        assertSame(row, row.withKeyMoved(0, 7, 0))
        assertSame(row, row.withKeyMoved(3, 0, 0))
    }

    @Test
    fun editingKeepsTheVisibilityOptions() {
        val config = two.copy(visible = false, onlyWithKeyboard = false)

        val edited = config.withKey(0, "ctrl").withoutKey(1, 0)

        assertFalse(edited.visible)
        assertFalse(edited.onlyWithKeyboard)
    }
}
