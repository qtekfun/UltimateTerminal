// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EdgeInsetsTest {
    @Test
    fun unionKeepsTheLargestValueOfEachEdge() {
        val bars = EdgeInsets(left = 0, top = 72, right = 0, bottom = 132)
        val keyboard = EdgeInsets(left = 0, top = 0, right = 0, bottom = 900)
        assertEquals(EdgeInsets(0, 72, 0, 900), bars union keyboard)
    }

    @Test
    fun theKeyboardReplacesTheNavigationBarInsteadOfAddingToIt() {
        val navigationBar = EdgeInsets(0, 0, 0, 132)
        val keyboard = EdgeInsets(0, 0, 0, 900)
        assertEquals(900, (navigationBar union keyboard).bottom)
    }

    @Test
    fun noneIsTheNeutralElement() {
        val insets = EdgeInsets(10, 20, 30, 40)
        assertEquals(insets, insets union EdgeInsets.NONE)
        assertEquals(insets, EdgeInsets.NONE union insets)
    }
}

class TerminalLayoutForTest {
    private val cellWidth = 12.5f
    private val cellHeight = 30

    private fun layout(width: Int, height: Int, insets: EdgeInsets = EdgeInsets.NONE) =
        terminalLayoutFor(width, height, insets, cellWidth, cellHeight)

    @Test
    fun aPhoneInPortrait() {
        // 1080x2400 with a 72 px status bar and a 132 px navigation bar.
        val layout = layout(1080, 2400, EdgeInsets(0, 72, 0, 132))
        assertEquals(GridSize(columns = 86, rows = 73), layout.grid)
    }

    @Test
    fun aTabletInLandscapeUsesTheWholeWidth() {
        // 2560x1600 with only a 48 px bottom navigation bar.
        val layout = layout(2560, 1600, EdgeInsets(0, 0, 0, 48))
        assertEquals(GridSize(columns = 204, rows = 51), layout.grid)
    }

    @Test
    fun theSameTabletInPortrait() {
        val layout = layout(1600, 2560, EdgeInsets(0, 0, 0, 48))
        assertEquals(GridSize(columns = 128, rows = 83), layout.grid)
    }

    @Test
    fun splitScreenGivesEachHalfItsOwnGrid() {
        val left = layout(1280, 1600)
        val right = layout(1280, 1600, EdgeInsets(0, 0, 0, 48))
        assertEquals(GridSize(columns = 102, rows = 53), left.grid)
        assertEquals(GridSize(columns = 102, rows = 51), right.grid)
    }

    @Test
    fun aFloatingWindowCanBeSmall() {
        assertEquals(GridSize(columns = 40, rows = 10), layout(500, 300).grid)
    }

    @Test
    fun theKeyboardTakesRowsAndNeverColumns() {
        val bars = EdgeInsets(0, 72, 0, 132)
        val without = layout(1080, 2400, bars)
        val with = layout(1080, 2400, bars union EdgeInsets(0, 0, 0, 1000))
        assertEquals(without.grid.columns, with.grid.columns)
        assertEquals(73, without.grid.rows)
        assertEquals(44, with.grid.rows)
    }

    @Test
    fun aDisplayCutoutOnTheSideTakesColumns() {
        val plain = layout(2400, 1080)
        val cutout = layout(2400, 1080, EdgeInsets(left = 110, top = 0, right = 0, bottom = 0))
        // (2400 - 110) / 12.5 = 183.2 cells, against 192 without the cutout.
        assertEquals(192, plain.grid.columns)
        assertEquals(183, cutout.grid.columns)
    }

    @Test
    fun insetsLargerThanTheWindowStillGiveTheMinimumGrid() {
        val layout = layout(300, 400, EdgeInsets(200, 300, 200, 300))
        assertEquals(GridSize(GridSize.MIN_COLUMNS, GridSize.MIN_ROWS), layout.grid)
    }

    @Test
    fun theCellSizeIsPassedOnToThePty() {
        val layout = layout(1000, 1000)
        assertEquals(12, layout.cellWidthPx)
        assertEquals(30, layout.cellHeightPx)
    }

    @Test
    fun `a text margin is taken from every edge`() {
        val inner = EdgeInsets(left = 10, top = 20, right = 30, bottom = 40).withTextMargin(6)

        assertEquals(EdgeInsets(left = 16, top = 26, right = 36, bottom = 46), inner)
    }

    @Test
    fun `a negative margin takes nothing`() {
        assertEquals(EdgeInsets.NONE, EdgeInsets.NONE.withTextMargin(-4))
    }

    @Test
    fun `the margin makes the grid smaller by the cells it covers, never bigger`() {
        val window = 1080 to 2000
        val plain = terminalLayoutFor(window.first, window.second, EdgeInsets.NONE, 15f, 40)
        val padded = terminalLayoutFor(
            window.first,
            window.second,
            EdgeInsets.NONE.withTextMargin(36),
            15f,
            40
        )

        assertTrue(padded.grid.columns <= plain.grid.columns)
        assertTrue(padded.grid.rows <= plain.grid.rows)
        assertEquals((1080 - 72) / 15, padded.grid.columns)
        assertEquals((2000 - 72) / 40, padded.grid.rows)
    }
}
