// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GridSizeTest {
    @Test
    fun countsOnlyWholeCells() {
        // 809 / 10 = 80.9 and 599 / 25 = 23.96: the partial cells are dropped.
        assertEquals(GridSize(columns = 80, rows = 23), gridSizeFor(809, 599, 10f, 25))
    }

    @Test
    fun fillsTheAreaWithoutOverflowing() {
        val grid = gridSizeFor(1920, 1080, 12.5f, 30)
        assertEquals(153, grid.columns)
        assertEquals(36, grid.rows)
    }

    @Test
    fun neverGoesBelowWhatTheEmulatorAccepts() {
        assertEquals(GridSize(GridSize.MIN_COLUMNS, GridSize.MIN_ROWS), gridSizeFor(5, 5, 10f, 20))
        assertEquals(GridSize(GridSize.MIN_COLUMNS, GridSize.MIN_ROWS), gridSizeFor(0, 0, 10f, 20))
    }

    @Test
    fun rejectsAnInvalidCellSize() {
        assertThrows(IllegalArgumentException::class.java) { gridSizeFor(100, 100, 0f, 10) }
        assertThrows(IllegalArgumentException::class.java) { gridSizeFor(100, 100, 5f, 0) }
    }
}

class ScrollingTest {
    @Test
    fun topRowStaysBetweenTheOldestLineAndTheLiveScreen() {
        assertEquals(0, clampTopRow(5, transcriptRows = 100))
        assertEquals(-30, clampTopRow(-30, transcriptRows = 100))
        assertEquals(-100, clampTopRow(-250, transcriptRows = 100))
    }

    @Test
    fun withoutHistoryTopRowIsAlwaysZero() {
        assertEquals(0, clampTopRow(-3, transcriptRows = 0))
        assertEquals(0, clampTopRow(-3, transcriptRows = -1))
    }

    @Test
    fun accumulatorCarriesTheRemainder() {
        val scroll = ScrollAccumulator()
        assertEquals(0, scroll.consume(10f, 20f)) // remainder 10
        assertEquals(1, scroll.consume(15f, 20f)) // 25 -> one line, remainder 5
        assertEquals(0, scroll.consume(4f, 20f)) // remainder 9
        assertEquals(0, scroll.consume(1f, 20f)) // remainder 10
        assertEquals(1, scroll.consume(10f, 20f)) // 20 -> one line, remainder 0
    }

    @Test
    fun accumulatorHandlesBothDirectionsAndBigJumps() {
        val scroll = ScrollAccumulator()
        assertEquals(-3, scroll.consume(-65f, 20f)) // remainder -5
        assertEquals(0, scroll.consume(-10f, 20f)) // remainder -15
        assertEquals(-1, scroll.consume(-10f, 20f)) // -25 -> one line up, remainder -5
        assertEquals(5, scroll.consume(105f, 20f)) // 100 -> five lines down
    }

    @Test
    fun resetDropsTheRemainder() {
        val scroll = ScrollAccumulator()
        scroll.consume(19f, 20f)
        scroll.reset()
        assertEquals(0, scroll.consume(1f, 20f))
    }

    @Test
    fun accumulatorRejectsAnInvalidLineHeight() {
        assertThrows(IllegalArgumentException::class.java) { ScrollAccumulator().consume(1f, 0f) }
    }
}

class TerminalSelectionTest {
    private fun cell(column: Int, row: Int) = CellPosition(column, row)

    @Test
    fun startAndEndAreOrderedWhateverTheDirection() {
        val forward = TerminalSelection(cell(2, 1), cell(5, 3))
        val backward = TerminalSelection(cell(5, 3), cell(2, 1))
        assertEquals(cell(2, 1), forward.start)
        assertEquals(cell(5, 3), forward.end)
        assertEquals(forward.start, backward.start)
        assertEquals(forward.end, backward.end)
    }

    @Test
    fun orderingOnTheSameRowUsesTheColumn() {
        val selection = TerminalSelection(cell(9, 4), cell(3, 4))
        assertEquals(cell(3, 4), selection.start)
        assertEquals(cell(9, 4), selection.end)
    }

    @Test
    fun columnsOnASingleRow() {
        assertEquals(3..9, TerminalSelection(cell(3, 4), cell(9, 4)).columnsOn(4, 80))
    }

    @Test
    fun columnsOnAMultiRowSelection() {
        val selection = TerminalSelection(cell(10, 1), cell(5, 3))
        assertEquals(10..79, selection.columnsOn(1, 80))
        assertEquals(0..79, selection.columnsOn(2, 80))
        assertEquals(0..5, selection.columnsOn(3, 80))
    }

    @Test
    fun rowsOutsideTheSelectionHaveNoColumns() {
        val selection = TerminalSelection(cell(10, 1), cell(5, 3))
        assertNull(selection.columnsOn(0, 80))
        assertNull(selection.columnsOn(4, 80))
    }

    @Test
    fun scrollbackRowsAreNegative() {
        val selection = TerminalSelection(cell(0, -2), cell(4, -1))
        assertEquals(0..79, selection.columnsOn(-2, 80))
        assertEquals(0..4, selection.columnsOn(-1, 80))
    }

    @Test
    fun withFocusMovesOnlyTheFocus() {
        val selection = TerminalSelection(cell(1, 1), cell(1, 1)).withFocus(cell(6, 2))
        assertEquals(cell(1, 1), selection.anchor)
        assertEquals(cell(6, 2), selection.focus)
    }
}

class ShellEnvironmentTest {
    @Test
    fun setsTheTerminalVariables() {
        val env = ShellEnvironment.build("/home/x", "/tmp/x", emptyMap()).toList()
        assertEquals(
            listOf(
                "COLORTERM=truecolor",
                "HOME=/home/x",
                "LANG=en_US.UTF-8",
                "PATH=/system/bin:/system/xbin",
                "TERM=xterm-256color",
                "TMPDIR=/tmp/x"
            ),
            env
        )
    }

    @Test
    fun passesThroughOnlyTheAndroidRuntimeVariables() {
        val env = ShellEnvironment.build(
            "/h",
            "/t",
            mapOf(
                "ANDROID_ROOT" to "/system",
                "ANDROID_DATA" to "/data",
                "SECRET_TOKEN" to "x",
                "PATH" to "/evil"
            )
        ).toList()
        assertEquals(true, "ANDROID_ROOT=/system" in env)
        assertEquals(true, "ANDROID_DATA=/data" in env)
        assertEquals(false, env.any { it.startsWith("SECRET_TOKEN") })
        assertEquals(true, "PATH=/system/bin:/system/xbin" in env)
    }

    @Test
    fun theResultDoesNotDependOnTheOrderOfTheInheritedMap() {
        val a = ShellEnvironment.build(
            "/h",
            "/t",
            linkedMapOf(
                "ANDROID_ROOT" to "/r",
                "ANDROID_DATA" to "/d"
            )
        )
        val b = ShellEnvironment.build(
            "/h",
            "/t",
            linkedMapOf(
                "ANDROID_DATA" to "/d",
                "ANDROID_ROOT" to "/r"
            )
        )
        assertEquals(a.toList(), b.toList())
    }
}
