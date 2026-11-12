// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VisibleSelectionTest {
    private fun selection(startColumn: Int, startRow: Int, endColumn: Int, endRow: Int) =
        TerminalSelection(CellPosition(startColumn, startRow), CellPosition(endColumn, endRow))

    @Test
    fun aSelectionOnOneVisibleRowKeepsItsColumns() {
        val visible = selection(3, 5, 9, 5).visibleIn(topRow = 0, rows = 24, columns = 80)
        assertEquals(
            VisibleSelection(firstRow = 5, lastRow = 5, firstColumn = 3, lastColumn = 9),
            visible
        )
    }

    @Test
    fun rowsAreCountedFromTheTopOfTheScreenWhenScrolledBack() {
        val visible = selection(0, -40, 4, -40).visibleIn(topRow = -50, rows = 24, columns = 80)
        assertEquals(10, visible?.firstRow)
        assertEquals(10, visible?.lastRow)
    }

    @Test
    fun aSelectionOverSeveralRowsSpansTheWholeWidth() {
        val visible = selection(70, 2, 5, 3).visibleIn(topRow = 0, rows = 24, columns = 80)
        assertEquals(
            VisibleSelection(firstRow = 2, lastRow = 3, firstColumn = 0, lastColumn = 79),
            visible
        )
    }

    @Test
    fun aSelectionLongerThanTheScreenIsClippedToIt() {
        // Select all over 500 lines of history: only the 24 rows on screen count.
        val visible = selection(0, -500, 79, 23).visibleIn(topRow = -100, rows = 24, columns = 80)
        assertEquals(
            VisibleSelection(firstRow = 0, lastRow = 23, firstColumn = 0, lastColumn = 79),
            visible
        )
    }

    @Test
    fun aSelectionThatStartsAboveTheScreenStartsAtItsTopRow() {
        val visible = selection(10, -30, 20, 4).visibleIn(topRow = 0, rows = 24, columns = 80)
        assertEquals(
            VisibleSelection(firstRow = 0, lastRow = 4, firstColumn = 0, lastColumn = 79),
            visible
        )
    }

    @Test
    fun theOnlyVisibleRowOfALongSelectionKeepsTheColumnsOfThatRow() {
        // The selection ends on the top row of the screen: only its columns 0..6 are there.
        val visible = selection(10, -30, 6, 0).visibleIn(topRow = 0, rows = 24, columns = 80)
        assertEquals(
            VisibleSelection(firstRow = 0, lastRow = 0, firstColumn = 0, lastColumn = 6),
            visible
        )
    }

    @Test
    fun columnsBeyondANarrowerScreenAreClamped() {
        val visible = selection(30, 1, 120, 1).visibleIn(topRow = 0, rows = 24, columns = 40)
        assertEquals(
            VisibleSelection(firstRow = 1, lastRow = 1, firstColumn = 30, lastColumn = 39),
            visible
        )
    }

    @Test
    fun aSelectionOffTheScreenIsNotVisible() {
        assertNull(selection(0, -80, 5, -60).visibleIn(topRow = 0, rows = 24, columns = 80))
        assertNull(selection(0, 2, 5, 3).visibleIn(topRow = -100, rows = 24, columns = 80))
    }

    @Test
    fun anEmptyScreenShowsNothing() {
        assertNull(selection(0, 0, 5, 0).visibleIn(topRow = 0, rows = 0, columns = 80))
        assertNull(selection(0, 0, 5, 0).visibleIn(topRow = 0, rows = 24, columns = 0))
    }
}

class EdgeScrollTest {
    @Test
    fun awayFromTheEdgesNothingScrolls() {
        assertEquals(0, edgeScrollLines(y = 500f, height = 1000f, edge = 40f))
        assertEquals(0, edgeScrollLines(y = 40f, height = 1000f, edge = 40f))
        assertEquals(0, edgeScrollLines(y = 960f, height = 1000f, edge = 40f))
    }

    @Test
    fun nearTheTopItGoesBackIntoTheHistory() {
        assertEquals(-1, edgeScrollLines(y = 20f, height = 1000f, edge = 40f))
        assertEquals(-2, edgeScrollLines(y = -10f, height = 1000f, edge = 40f))
    }

    @Test
    fun nearTheBottomItGoesTowardsTheLiveScreen() {
        assertEquals(1, edgeScrollLines(y = 980f, height = 1000f, edge = 40f))
        assertEquals(2, edgeScrollLines(y = 1010f, height = 1000f, edge = 40f))
    }

    @Test
    fun farPastTheEdgeItIsCapped() {
        assertEquals(
            -MAX_EDGE_SCROLL_LINES,
            edgeScrollLines(y = -5000f, height = 1000f, edge = 40f)
        )
        assertEquals(MAX_EDGE_SCROLL_LINES, edgeScrollLines(y = 9000f, height = 1000f, edge = 40f))
    }

    @Test
    fun rejectsAnEmptyEdge() {
        assertThrows(IllegalArgumentException::class.java) { edgeScrollLines(1f, 10f, 0f) }
    }
}
