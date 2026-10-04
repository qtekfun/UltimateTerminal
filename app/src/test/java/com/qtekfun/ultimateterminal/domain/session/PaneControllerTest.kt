// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation.HORIZONTAL
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation.VERTICAL
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class Pty : SessionHandle {
    val resizes = mutableListOf<TerminalLayout>()
    var stopped = false

    override fun resize(layout: TerminalLayout) {
        resizes += layout
    }

    override fun stop() {
        stopped = true
    }
}

private class Ptys : SessionFactory {
    val byId = mutableMapOf<SessionId, Pty>()

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle = Pty().also { byId[id] = it }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PaneControllerTest {
    private val ptys = Ptys()
    private val sessions = SessionController(ptys, { })
    private val area = PaneArea(
        widthPx = 1001,
        heightPx = 600,
        cellWidthPx = 10f,
        cellHeightPx = 20,
        dividerPx = 1
    )

    private fun TestScope.panes() = PaneController(sessions, backgroundScope, debounceMillis = 100)

    /** Lets the debounce of the pane sizes pass. */
    private fun TestScope.settle() {
        advanceTimeBy(101)
        runCurrent()
    }

    @Test
    fun withoutAnAreaThereIsNoScene() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        sessions.newSession()

        assertNull(panes.scene.value)
    }

    @Test
    fun aTabThatIsNotSplitIsOnePaneOverTheWholeArea() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()

        panes.onArea(area)

        assertEquals(listOf(PaneBox(first, PaneRect(0, 0, 1001, 600))), panes.scene.value?.panes)
    }

    @Test
    fun splittingStartsAShellInTheNewPaneAndSizesEachPane() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.onArea(area)

        panes.splitVertical()
        settle()

        val second = sessions.state.value.activeId!!
        assertTrue(ptys.byId.containsKey(second))
        assertEquals(second, panes.focused.value)
        assertEquals(2, panes.scene.value?.panes?.size)
        assertEquals(50, ptys.byId.getValue(first).resizes.last().grid.columns)
        assertEquals(50, ptys.byId.getValue(second).resizes.last().grid.columns)
    }

    @Test
    fun aPaneIsOnlyResizedWhenItsSizeChanges() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.onArea(area)
        panes.splitVertical()
        settle()
        val before = ptys.byId.getValue(first).resizes.size

        panes.onArea(area.copy())
        settle()
        panes.focus(FocusDirection.Left)
        settle()

        assertEquals(before, ptys.byId.getValue(first).resizes.size)
    }

    @Test
    fun aKeyboardAnimationOnlyDeliversTheLastSizeOfEveryPane() =
        runTest(UnconfinedTestDispatcher()) {
            val panes = panes()
            val first = sessions.newSession()
            panes.onArea(area)
            panes.splitHorizontal()
            settle()
            val before = ptys.byId.getValue(first).resizes.size

            for (height in 590 downTo 400 step 10) panes.onArea(area.copy(heightPx = height))
            advanceTimeBy(99)
            assertEquals(before, ptys.byId.getValue(first).resizes.size)
            advanceTimeBy(2)

            assertEquals(before + 1, ptys.byId.getValue(first).resizes.size)
            assertEquals(10, ptys.byId.getValue(first).resizes.last().grid.rows)
        }

    @Test
    fun aSplitThatLeavesTooLittleRoomIsRefused() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        sessions.newSession()
        panes.onArea(area.copy(widthPx = 300))
        val refusals = mutableListOf<SplitRefusal>()
        backgroundScope.launch { panes.refusals.toList(refusals) }

        panes.splitVertical()

        assertEquals(listOf(SplitRefusal.TooSmall), refusals)
        assertEquals(1, sessions.state.value.items.size)
    }

    @Test
    fun withoutAKnownAreaASplitIsNotRefused() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        sessions.newSession()

        panes.splitHorizontal()

        assertEquals(2, sessions.state.value.items.size)
    }

    @Test
    fun focusMovesToTheNeighbourAndTappingAPaneFocusesIt() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.onArea(area)
        panes.splitVertical()
        val second = sessions.state.value.activeId!!

        panes.focus(FocusDirection.Left)
        assertEquals(first, panes.focused.value)
        panes.focus(FocusDirection.Left)
        assertEquals(first, panes.focused.value)
        panes.focusPane(second)
        assertEquals(second, panes.focused.value)
    }

    @Test
    fun draggingADividerChangesTheRatioWithinTheLimits() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.onArea(area)
        panes.splitVertical()
        val divider = panes.scene.value!!.dividers.single()

        panes.dragDivider(divider, pointerPx = 300f)
        assertEquals(0.3f, (sessions.state.value.treeOf(first) as PaneNode.Branch).ratio, 0.01f)

        panes.dragDivider(divider, pointerPx = 2f)
        val minimum = (sessions.state.value.treeOf(first) as PaneNode.Branch).ratio
        assertEquals(0.2f, minimum, 0.01f)
    }

    @Test
    fun draggingBeforeTheAreaIsKnownDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.splitVertical()
        val tree = sessions.state.value.treeOf(first)

        panes.dragDivider(
            Divider(DividerPath(emptyList()), VERTICAL, PaneRect(0, 0, 1, 1), PaneRect(0, 0, 9, 9)),
            pointerPx = 3f
        )

        assertEquals(tree, sessions.state.value.treeOf(first))
    }

    @Test
    fun swappingExchangesThePaneWithItsNeighbour() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.onArea(area)
        panes.splitVertical()
        val second = sessions.state.value.activeId!!

        panes.swap(FocusDirection.Left)

        assertEquals(listOf(second, first), sessions.state.value.paneIdsOf(first))
        assertEquals(second, panes.focused.value)
        panes.swap(FocusDirection.Left)
        assertEquals(listOf(second, first), sessions.state.value.paneIdsOf(first))
    }

    @Test
    fun zoomShowsOnePaneAndSizesItToTheWholeArea() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        sessions.newSession()
        panes.onArea(area)
        panes.splitVertical()
        val second = sessions.state.value.activeId!!

        panes.toggleZoom()
        settle()

        assertEquals(listOf(PaneBox(second, PaneRect(0, 0, 1001, 600))), panes.scene.value?.panes)
        assertEquals(100, ptys.byId.getValue(second).resizes.last().grid.columns)
        panes.toggleZoom()
        settle()
        assertEquals(2, panes.scene.value?.panes?.size)
        assertEquals(50, ptys.byId.getValue(second).resizes.last().grid.columns)
    }

    @Test
    fun closingAPaneAsksWhileItsShellRunsAndTheTabIsKept() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.onArea(area)
        panes.splitVertical()
        val second = sessions.state.value.activeId!!

        panes.closePane()
        assertEquals(second, panes.closing.pending.value)
        panes.closing.dismiss()
        assertNull(panes.closing.pending.value)
        assertEquals(2, sessions.state.value.items.size)

        panes.closePane()
        panes.closing.confirm()

        assertNull(panes.closing.pending.value)
        assertEquals(listOf(first), sessions.state.value.items.map { it.id })
        assertTrue(ptys.byId.getValue(second).stopped)
    }

    @Test
    fun closingAPaneOfATabThatIsNotSplitDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        sessions.newSession()

        panes.closePane()

        assertNull(panes.closing.pending.value)
        assertEquals(1, sessions.state.value.items.size)
        assertNotNull(sessions.state.value.activeId)
    }

    @Test
    fun theHorizontalSplitPutsTheNewPaneBelow() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.onArea(area)

        panes.splitHorizontal()

        val tree = sessions.state.value.treeOf(first) as PaneNode.Branch
        assertEquals(HORIZONTAL, tree.orientation)
    }

    @Test
    fun aPaneThatEndedCanBeClosedWithoutAsking() = runTest(UnconfinedTestDispatcher()) {
        val panes = panes()
        val first = sessions.newSession()
        panes.splitVertical()
        val second = sessions.state.value.activeId!!
        sessions.edit { exited(second, 0) }

        panes.closePane()

        assertNull(panes.closing.pending.value)
        assertEquals(listOf(first), sessions.state.value.items.map { it.id })
    }

    @Test
    fun theWholeAreaSizeIsKeptForALoneTabByTheControllerOnLayout() =
        runTest(UnconfinedTestDispatcher()) {
            val panes = panes()
            val first = sessions.newSession()
            panes.onArea(area)
            val layout = TerminalLayout(GridSize(100, 30), 10, 20)

            sessions.onLayout(layout)

            assertEquals(layout, ptys.byId.getValue(first).resizes.last())
        }
}

class SessionControllerPanesTest {
    private val ptys = Ptys()
    private val serviceCalls = mutableListOf<Boolean>()
    private val controller = SessionController(ptys, { serviceCalls += it })
    private val small = TerminalLayout(GridSize(40, 10), 10, 20)
    private val wide = TerminalLayout(GridSize(100, 30), 10, 20)

    @Test
    fun splittingStartsAShellThatBecomesTheActiveOne() {
        val first = controller.newSession()

        val second = controller.splitActive(VERTICAL)!!

        assertEquals(second, controller.state.value.activeId)
        assertEquals(listOf(first, second), controller.state.value.paneIdsOf(first))
        assertEquals(listOf(true), serviceCalls)
    }

    @Test
    fun splittingWithNoSessionDoesNothing() {
        assertNull(controller.splitActive(VERTICAL))
        assertTrue(ptys.byId.isEmpty())
    }

    @Test
    fun theWholeAreaSizeDoesNotReachThePanesOfASplitTab() {
        val first = controller.newSession()
        val second = controller.splitActive(VERTICAL)!!

        controller.onLayout(wide)

        assertTrue(ptys.byId.getValue(first).resizes.isEmpty())
        assertTrue(ptys.byId.getValue(second).resizes.isEmpty())
    }

    @Test
    fun comingBackToASplitTabDoesNotResizeItsPaneToTheWholeArea() {
        val first = controller.newSession()
        val second = controller.splitActive(VERTICAL)!!
        controller.newSession()

        controller.edit { activated(first) }

        assertEquals(second, controller.state.value.activeId)
        assertTrue(ptys.byId.getValue(second).resizes.isEmpty())
    }

    @Test
    fun aPlainTabIsSizedToTheWholeAreaWhenItComesToTheFront() {
        val first = controller.newSession()
        controller.newSession()

        controller.edit { activated(first) }

        assertEquals(controller.layout, ptys.byId.getValue(first).resizes.last())
    }

    @Test
    fun eachPaneGetsItsOwnSizeAndAnUnchangedOneIsNotSentAgain() {
        val first = controller.newSession()
        val second = controller.splitActive(VERTICAL)!!

        controller.applyPaneLayouts(mapOf(first to small, second to wide))
        controller.applyPaneLayouts(mapOf(first to small, second to small))

        assertEquals(listOf(small), ptys.byId.getValue(first).resizes)
        assertEquals(listOf(wide, small), ptys.byId.getValue(second).resizes)
    }

    @Test
    fun aSizeForAPaneThatNoLongerExistsIsIgnored() {
        controller.newSession()

        controller.applyPaneLayouts(mapOf(SessionId(99) to small))

        assertTrue(ptys.byId.values.all { it.resizes.isEmpty() })
    }

    @Test
    fun aClosedPaneIsStoppedAndTheOthersAreNotResizedAgain() {
        val first = controller.newSession()
        val second = controller.splitActive(VERTICAL)!!
        controller.applyPaneLayouts(mapOf(first to small, second to small))

        controller.close(second)
        controller.applyPaneLayouts(mapOf(first to small))

        assertEquals(listOf(small), ptys.byId.getValue(first).resizes)
        assertTrue(ptys.byId.getValue(second).stopped)
    }

    @Test
    fun closingATabEndsItsPanesAndItself() {
        val first = controller.newSession()
        val second = controller.splitActive(HORIZONTAL)!!
        val other = controller.newSession()

        controller.closeTab(first)

        assertEquals(listOf(other), controller.state.value.items.map { it.id })
        assertTrue(ptys.byId.getValue(first).stopped)
        assertTrue(ptys.byId.getValue(second).stopped)
        assertTrue(!ptys.byId.getValue(other).stopped)
    }

    @Test
    fun closingEverythingStopsTheServiceAndForgetsTheSizes() {
        val first = controller.newSession()
        val second = controller.splitActive(VERTICAL)!!
        controller.applyPaneLayouts(mapOf(first to small, second to small))

        controller.closeAll()

        assertEquals(listOf(true, false), serviceCalls)
        assertTrue(controller.state.value.items.isEmpty())
    }
}
