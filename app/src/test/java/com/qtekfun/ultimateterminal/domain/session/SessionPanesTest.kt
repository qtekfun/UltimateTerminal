// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation.HORIZONTAL
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation.VERTICAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionPanesTest {
    /** One tab, `first`, then split twice: first | (second over third). Third has the keyboard. */
    private class Split3 {
        private val created = Sessions().created(distroId = 7)
        val one = created.first
        val first = created.second
        private val firstSplit = one.split(VERTICAL)!!
        val second = firstSplit.second
        private val secondSplit = firstSplit.first.focused(second).split(HORIZONTAL)!!
        val three = secondSplit.first
        val third = secondSplit.second
    }

    @Test
    fun splittingAddsAShellInTheSameDistroThatGetsTheKeyboard() {
        val (sessions, first) = Sessions().created(distroId = 7)

        val (split, second) = sessions.split(VERTICAL)!!

        assertEquals(listOf(first, second), split.items.map { it.id })
        assertEquals(7L, split.items.last().distroId)
        assertEquals(second, split.activeId)
        assertEquals(SessionState.Running, split.items.last().state)
        assertEquals(PaneNode.Leaf(first).split(first, VERTICAL, second), split.treeOf(first))
    }

    @Test
    fun splittingWithNothingActiveDoesNothing() {
        assertNull(Sessions().split(VERTICAL))
    }

    @Test
    fun aSplitPaneIsNotATabOfItsOwn() {
        val s = Split3()

        assertEquals(listOf(s.first), s.three.tabs.map { it.id })
        assertEquals(s.first, s.three.tabOf(s.third))
        assertEquals(listOf(s.first, s.second, s.third), s.three.paneIdsOf(s.first))
    }

    @Test
    fun theTabBarShowsOneTabPerSplitTabAndItsActiveFollowsTheKeyboard() {
        val s = Split3()
        val (withOther, other) = s.three.created()

        val items = tabItems(withOther, emptyList())

        assertEquals(listOf(s.first, other), items.map { it.id })
        assertEquals(listOf(false, true), items.map { it.active })
        assertEquals(listOf(1, 2), items.map { it.position })
        val back = tabItems(withOther.focused(s.second), emptyList())
        assertEquals(listOf(true, false), back.map { it.active })
    }

    @Test
    fun aTabRunsWhileAnyOfItsPanesDoes() {
        val s = Split3()
        val ended = s.three.exited(s.first, 0).exited(s.second, 0)

        assertTrue(tabItems(ended, emptyList()).single().running)
        assertFalse(tabItems(ended.exited(s.third, 0), emptyList()).single().running)
    }

    @Test
    fun selectingATabReturnsToThePaneThatHadTheKeyboard() {
        val s = Split3()
        val (withOther, other) = s.three.created()

        assertEquals(other, withOther.activeId)
        assertEquals(s.third, withOther.activated(s.first).activeId)
        assertEquals(
            s.second,
            withOther.focused(s.second).activated(other).activated(s.first).activeId
        )
    }

    @Test
    fun focusingAPaneKeepsTheTabAndIsRememberedByIt() {
        val s = Split3()

        val moved = s.three.focused(s.first)

        assertEquals(s.first, moved.activeId)
        assertEquals(s.first, moved.focusOf(s.first))
        assertEquals(s.three, s.three.focused(SessionId(99)))
    }

    @Test
    fun closingAPaneLetsItsSiblingTakeTheSpaceAndMovesTheKeyboard() {
        val s = Split3()

        val closed = s.three.closed(s.third)

        assertEquals(listOf(s.first, s.second), closed.items.map { it.id })
        assertEquals(s.second, closed.activeId)
        assertEquals(
            PaneNode.Leaf(s.first).split(s.first, VERTICAL, s.second),
            closed.treeOf(s.first)
        )
    }

    @Test
    fun closingAPaneThatDoesNotHaveTheKeyboardLeavesItAlone() {
        val s = Split3()

        val closed = s.three.closed(s.first)

        assertEquals(s.third, closed.activeId)
    }

    @Test
    fun whenOnlyOnePaneIsLeftTheTabIsAPlainSessionAgain() {
        val s = Split3()

        val plain = s.three.closed(s.third).closed(s.second)

        assertTrue(plain.panes.isEmpty())
        assertEquals(listOf(s.first), plain.tabs.map { it.id })
        assertEquals(s.first, plain.activeId)
    }

    @Test
    fun closingTheTabsOwnPanePromotesTheNextOneKeepingPlaceAndName() {
        val s = Split3()
        val (withOther, other) = s.three.created()
        val named = withOther.renamed(s.first, "logs")

        val closed = named.closed(s.first)

        assertEquals(listOf(s.second, other), closed.tabs.map { it.id })
        assertEquals("logs", closed.tabs.first().title)
        assertEquals(listOf(s.second, s.third), closed.paneIdsOf(s.second))
        assertEquals(other, closed.activeId)
        assertEquals(s.third, closed.activated(s.second).activeId)
    }

    @Test
    fun closingTheTabsOwnPaneRekeysTheSplitUnderThePromotedPane() {
        val s = Split3()

        val closed = s.three.closed(s.first)

        assertEquals(listOf(s.second, s.third), closed.items.map { it.id })
        assertEquals(setOf(s.second), closed.panes.keys)
    }

    @Test
    fun closingTheLastSessionOfAPlainTabPicksARunningTabAndItsKeyboardPane() {
        val s = Split3()
        val (withOther, other) = s.three.created()
        val onlyOtherEnds = withOther.exited(other, 1)

        val closed = onlyOtherEnds.closed(other)

        assertEquals(s.third, closed.activeId)
        assertEquals(listOf(s.first), closed.tabs.map { it.id })
    }

    @Test
    fun nextAndPreviousStepOverTabsNotOverPanes() {
        val s = Split3()
        val (withOther, other) = s.three.created()

        assertEquals(s.third, withOther.switched(TabSwitch.Next).activeId)
        assertEquals(other, withOther.switched(TabSwitch.Next).switched(TabSwitch.Next).activeId)
        assertEquals(s.third, withOther.switched(TabSwitch.Previous).activeId)
        assertEquals(s.third, withOther.switched(TabSwitch.Number(1)).activeId)
        assertEquals(withOther, withOther.switched(TabSwitch.Number(3)))
    }

    @Test
    fun movingATabKeepsItsPanes() {
        val s = Split3()
        val (withOther, other) = s.three.created()

        val moved = withOther.moved(other, 0)

        assertEquals(listOf(other, s.first), moved.tabs.map { it.id })
        assertEquals(listOf(s.first, s.second, s.third), moved.paneIdsOf(s.first))
        assertEquals(s.three.items.size + 1, moved.items.size)
    }

    @Test
    fun closingATabAsksWhileAnyPaneRuns() {
        val s = Split3()
        val onlyOneRuns = s.three.exited(s.first, 0).exited(s.second, 0)

        assertEquals(CloseAction.Confirm, onlyOneRuns.closeAction(s.first))
        assertEquals(CloseAction.Close, onlyOneRuns.exited(s.third, 0).closeAction(s.first))
        assertEquals(CloseAction.Close, onlyOneRuns.closeAction(s.second))
        assertEquals(CloseAction.Ignore, onlyOneRuns.closeAction(SessionId(99)))
    }

    @Test
    fun aWholeTabClosesItsPanesBeforeItself() {
        val s = Split3()

        assertEquals(listOf(s.second, s.third, s.first), s.three.tabCloseOrder(s.first))
        assertEquals(listOf(s.second), s.three.tabCloseOrder(s.second))
    }

    @Test
    fun closingEveryPaneThenTheTabLeavesNothing() {
        val s = Split3()

        val none = s.three.tabCloseOrder(s.first).fold(s.three) { acc, id -> acc.closed(id) }

        assertTrue(none.items.isEmpty())
        assertTrue(none.panes.isEmpty())
        assertNull(none.activeId)
    }

    @Test
    fun aDividerMoveAppliesToTheActiveTabOnly() {
        val s = Split3()

        val moved = s.three.ratioSet(DividerPath(emptyList()), 0.3f)

        assertEquals(0.3f, (moved.treeOf(s.first) as PaneNode.Branch).ratio)
        assertEquals(s.one, s.one.ratioSet(DividerPath(emptyList()), 0.3f))
        assertEquals(Sessions(), Sessions().ratioSet(DividerPath(emptyList()), 0.3f))
    }

    @Test
    fun swappingPanesExchangesTheirPlaces() {
        val s = Split3()

        val swapped = s.three.swappedPanes(s.first, s.third)

        assertEquals(listOf(s.third, s.second, s.first), swapped.paneIdsOf(s.first))
        assertEquals(s.third, swapped.activeId)
        assertEquals(s.three, s.three.swappedPanes(s.first, SessionId(99)))
        assertEquals(s.one, s.one.swappedPanes(s.first, s.first))
    }

    @Test
    fun zoomShowsOnlyThePaneThatHasTheKeyboardAndASplitUndoesIt() {
        val s = Split3()

        val zoomed = s.three.zoomToggled()

        assertEquals(PaneNode.Leaf(s.third), zoomed.visibleTree(s.first))
        assertEquals(s.three.treeOf(s.first), zoomed.treeOf(s.first))
        assertEquals(s.three, zoomed.zoomToggled())
        assertEquals(s.one, s.one.zoomToggled())
        assertEquals(PaneNode.Leaf(s.first), s.one.visibleTree(s.first))
    }

    @Test
    fun aTabWithoutPanesBehavesAsBefore() {
        val (a, first) = Sessions().created()
        val (b, second) = a.created()

        assertEquals(listOf(first, second), b.tabs.map { it.id })
        assertEquals(first, b.closed(second).activeId)
        assertEquals(first, b.closed(second).fallbackTab())
        assertNull(Sessions().fallbackTab())
    }
}
