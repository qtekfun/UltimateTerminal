// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MenuPlacementTest {
    private val window = IntSize(1000, 2000)
    private val menu = IntSize(300, 800)

    @Test
    fun `the menu hangs from the right edge of an anchor in the top bar`() {
        val anchor = IntRect(850, 0, 950, 100)
        assertEquals(IntOffset(650, 0), menuPlacement(anchor, window, menu))
    }

    @Test
    fun `an anchor in a rail at the left edge opens the menu beside it`() {
        val anchor = IntRect(0, 300, 150, 450)
        assertEquals(IntOffset(150, 300), menuPlacement(anchor, window, menu))
    }

    @Test
    fun `an anchor near the bottom moves the menu up to stay on screen`() {
        val anchor = IntRect(850, 1800, 950, 1900)
        assertEquals(IntOffset(650, 1200), menuPlacement(anchor, window, menu))
    }

    @Test
    fun `an anchor partly outside the window keeps the menu inside`() {
        val anchor = IntRect(1000, 0, 1200, 100)
        assertEquals(IntOffset(700, 0), menuPlacement(anchor, window, menu))
    }

    @Test
    fun `a menu bigger than the window sits at the origin`() {
        val tiny = IntSize(200, 300)
        assertEquals(IntOffset(0, 0), menuPlacement(IntRect(0, 50, 40, 90), tiny, IntSize(300, 800)))
    }
}
