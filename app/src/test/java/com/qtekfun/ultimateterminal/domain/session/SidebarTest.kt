// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import com.qtekfun.ultimateterminal.domain.terminal.terminalLayoutFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SidebarTest {
    @Test
    fun `using the terminal collapses the sidebar in the auto mode`() {
        val open = SidebarState.expandedAfter(SidebarEvent.TERMINAL_USED, SidebarMode.AUTO_COLLAPSE)

        assertFalse(open)
    }

    @Test
    fun `asking for it opens the sidebar`() {
        assertTrue(SidebarState.expandedAfter(SidebarEvent.EXPAND, SidebarMode.AUTO_COLLAPSE))
        assertTrue(
            SidebarState.expandedAfter(SidebarEvent.TAB_ACTIONS_NEEDED, SidebarMode.AUTO_COLLAPSE)
        )
    }

    @Test
    fun `the chevron collapses it`() {
        assertFalse(SidebarState.expandedAfter(SidebarEvent.COLLAPSE, SidebarMode.AUTO_COLLAPSE))
    }

    @Test
    fun `the always expanded mode ignores every event`() {
        SidebarEvent.entries.forEach {
            assertTrue(SidebarState.expandedAfter(it, SidebarMode.ALWAYS_EXPANDED), it.name)
        }
    }

    @Test
    fun `switching to always expanded opens a collapsed sidebar and keeps an open one`() {
        assertTrue(SidebarState.onMode(false, SidebarMode.ALWAYS_EXPANDED))
        assertTrue(SidebarState.onMode(true, SidebarMode.ALWAYS_EXPANDED))
    }

    @Test
    fun `switching to the auto mode leaves the state as it is`() {
        assertTrue(SidebarState.onMode(true, SidebarMode.AUTO_COLLAPSE))
        assertFalse(SidebarState.onMode(false, SidebarMode.AUTO_COLLAPSE))
    }

    @Test
    fun `a stored name gives its mode and anything else the default`() {
        assertEquals(SidebarMode.ALWAYS_EXPANDED, SidebarMode.parse("ALWAYS_EXPANDED"))
        assertEquals(SidebarMode.AUTO_COLLAPSE, SidebarMode.parse("AUTO_COLLAPSE"))
        assertEquals(SidebarMode.DEFAULT, SidebarMode.parse("sideways"))
        assertEquals(SidebarMode.DEFAULT, SidebarMode.parse(null))
        assertEquals(SidebarMode.AUTO_COLLAPSE, SidebarMode.DEFAULT)
    }

    @Test
    fun `the rail is narrower than the open bar and still a touch target`() {
        assertEquals(192, SidebarWidths.targetDp(true))
        assertEquals(56, SidebarWidths.targetDp(false))
        assertTrue(SidebarWidths.RAIL_DP >= 48)
    }

    @Test
    fun `the area slides from where the bar is to where it will be`() {
        assertEquals(250f, slideOffsetPx(400f, 150), 0f)
        assertEquals(-250f, slideOffsetPx(150f, 400), 0f)
        assertEquals(0f, slideOffsetPx(150f, 150), 0f)
    }

    @Test
    fun `the grid is computed once for the width the bar will have`() {
        val window = 1_000
        val cell = 10f
        val density = 2.5f
        fun columns(expanded: Boolean): Int {
            val bar = (SidebarWidths.targetDp(expanded) * density).toInt()
            val insets = EdgeInsets(0, 0, 0, 0).reserveForTabBar(TabBarPlacement.Side, bar)
            return terminalLayoutFor(window, 800, insets, cell, 20).grid.columns
        }

        // 1000 - 480 = 520 px of 10 px cells; 1000 - 140 = 860 px.
        assertEquals(52, columns(expanded = true))
        assertEquals(86, columns(expanded = false))
        assertTrue(columns(expanded = false) > columns(expanded = true))
    }

    @Test
    fun `a tab shows its first letter or digit in capitals`() {
        assertEquals("A", tabInitial("alpine"))
        assertEquals("3", tabInitial("3rd"))
        assertEquals("D", tabInitial("  »debian"))
        assertEquals("?", tabInitial(""))
        assertEquals("?", tabInitial("..."))
    }
}
