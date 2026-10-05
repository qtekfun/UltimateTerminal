// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.integration

import com.qtekfun.ultimateterminal.domain.session.PaneArea
import com.qtekfun.ultimateterminal.domain.session.PaneController
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionFactory
import com.qtekfun.ultimateterminal.domain.session.SessionHandle
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.terminal.terminalLayoutFor
import com.qtekfun.ultimateterminal.domain.terminal.withTextMargin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A pty that only remembers the sizes it was told, as the kernel would receive them. */
private class FakePty : SessionHandle {
    val sizes = mutableListOf<TerminalLayout>()

    override fun resize(layout: TerminalLayout) {
        sizes += layout
    }

    override fun stop() = Unit
}

private class FakePtys : SessionFactory {
    val byId = mutableMapOf<SessionId, FakePty>()

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle = FakePty().also {
        it.sizes += layout
        byId[id] = it
    }
}

/**
 * From the size of the window to the size each pty is told: the insets the window loses to the
 * system bars, the keyboard and the text margin, the grid that fits, and the split of the area
 * between panes. The pty is the end of the chain, so the assertions are on its calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResizeIntegrationTest {
    private val ptys = FakePtys()
    private val sessions = SessionController(ptys, { })

    private val cellWidth = 10f
    private val cellHeight = 20
    private val statusBar = EdgeInsets(0, 48, 0, 0)
    private val navigationBar = EdgeInsets(0, 0, 0, 40)
    private val keyboard = EdgeInsets(0, 0, 0, 400)
    private val margin = 4

    /** What the screen does with a window: the insets of the bars and the keyboard are combined. */
    private fun insets(keyboardShown: Boolean) =
        (statusBar union navigationBar union if (keyboardShown) keyboard else EdgeInsets.NONE)
            .withTextMargin(margin)

    private fun layoutOf(width: Int, height: Int, keyboardShown: Boolean = false) =
        terminalLayoutFor(width, height, insets(keyboardShown), cellWidth, cellHeight)

    private fun TestScope.settle() {
        advanceTimeBy(121)
        runCurrent()
    }

    @Test
    fun theWindowSizeBecomesThePtyGrid() {
        sessions.onLayout(layoutOf(1080, 2000))
        val id = sessions.newSession()

        // 1080 - 8 margin = 1072 px -> 107 columns; 2000 - 48 - 40 - 8 = 1904 px -> 95 rows.
        assertEquals(GridSize(107, 95), ptys.byId.getValue(id).sizes.last().grid)
    }

    @Test
    fun rotatingTheWindowAndShowingTheKeyboardResizeTheRunningPty() {
        sessions.onLayout(layoutOf(1080, 2000))
        val id = sessions.newSession()

        sessions.onLayout(layoutOf(2000, 1080))
        sessions.onLayout(layoutOf(2000, 1080, keyboardShown = true))

        // Landscape: 1992 px -> 199 columns; rows 1080 - 48 - 40 - 8 = 984 -> 49.
        // With the keyboard (400 px replaces the 40 px navigation bar): 1080 - 48 - 400 - 8 = 624 -> 31.
        assertEquals(
            listOf(GridSize(107, 95), GridSize(199, 49), GridSize(199, 31)),
            ptys.byId.getValue(id).sizes.map { it.grid }
        )
    }

    @Test
    fun aTabShownLaterStartsAtTheLastReportedSize() {
        sessions.onLayout(layoutOf(1080, 2000))
        sessions.newSession()
        sessions.onLayout(layoutOf(2000, 1080))

        val second = sessions.newSession()

        assertEquals(listOf(GridSize(199, 49)), ptys.byId.getValue(second).sizes.map { it.grid })
    }

    @Test
    fun splitPanesShareTheAreaAndEachPtyGetsItsOwnSize() = runTest(UnconfinedTestDispatcher()) {
        val panes = PaneController(sessions, backgroundScope, debounceMillis = 100)
        val first = sessions.newSession()
        fun area(width: Int, height: Int) =
            PaneArea(width, height, cellWidth, cellHeight, dividerPx = 1)
        panes.onArea(area(1001, 600))

        panes.splitVertical()
        settle()

        val second = sessions.state.value.activeId!!
        // 1001 px less a 1 px divider, half each: 500 px -> 50 columns; 600 px -> 30 rows.
        assertEquals(GridSize(50, 30), ptys.byId.getValue(first).sizes.last().grid)
        assertEquals(GridSize(50, 30), ptys.byId.getValue(second).sizes.last().grid)

        // The window is rotated: every pane is told, once, the size of its new area.
        panes.onArea(area(601, 1000))
        settle()
        assertEquals(GridSize(30, 50), ptys.byId.getValue(first).sizes.last().grid)
        assertEquals(GridSize(30, 50), ptys.byId.getValue(second).sizes.last().grid)

        // A horizontal split of the focused pane divides its height, not its width.
        panes.splitHorizontal()
        settle()
        val third = sessions.state.value.activeId!!
        assertEquals(GridSize(30, 24), ptys.byId.getValue(third).sizes.last().grid)
        assertEquals(GridSize(30, 50), ptys.byId.getValue(first).sizes.last().grid)
    }

    @Test
    fun aKeyboardAnimationIsOneResizePerPaneNotOnePerFrame() = runTest(UnconfinedTestDispatcher()) {
        val panes = PaneController(sessions, backgroundScope, debounceMillis = 100)
        val first = sessions.newSession()
        fun area(height: Int) = PaneArea(1001, height, cellWidth, cellHeight, dividerPx = 1)
        panes.onArea(area(1000))
        panes.splitHorizontal()
        settle()
        val before = ptys.byId.getValue(first).sizes.size

        for (height in 990 downTo 600 step 30) panes.onArea(area(height))
        settle()

        assertEquals(before + 1, ptys.byId.getValue(first).sizes.size)
        assertEquals(15, ptys.byId.getValue(first).sizes.last().grid.rows)
    }
}
