// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.data.local.LayoutCodec
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation.HORIZONTAL
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation.VERTICAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaneTreeTest {
    private val a = SessionId(1)
    private val b = SessionId(2)
    private val c = SessionId(3)
    private val d = SessionId(4)
    private val leafA = PaneNode.Leaf(a)

    /** a | (b over c) */
    private val three: PaneNode = PaneNode.Leaf(a)
        .split(a, VERTICAL, b)
        .split(b, HORIZONTAL, c)

    @Test
    fun splittingKeepsThePaneAndPutsTheNewOneAfterIt() {
        val tree = leafA.split(a, VERTICAL, b)

        assertEquals(PaneNode.Branch(VERTICAL, 0.5f, leafA, PaneNode.Leaf(b)), tree)
        assertEquals(listOf(a, b), tree.leaves())
    }

    @Test
    fun splittingAnUnknownPaneChangesNothing() {
        assertEquals(three, three.split(d, VERTICAL, SessionId(9)))
    }

    @Test
    fun leavesAreInReadingOrder() {
        assertEquals(listOf(a, b, c), three.leaves())
        assertTrue(c in three)
        assertTrue(d !in three)
    }

    @Test
    fun closingAPaneLetsItsSiblingTakeTheSpace() {
        assertEquals(PaneNode.Branch(VERTICAL, 0.5f, leafA, PaneNode.Leaf(c)), three.removed(b))
    }

    @Test
    fun closingTheOnlyPaneLeavesNothing() {
        assertNull(leafA.removed(a))
    }

    @Test
    fun closingAnUnknownPaneChangesNothing() {
        assertEquals(three, three.removed(d))
    }

    @Test
    fun closingTheFirstOfTwoLeavesTheOther() {
        assertEquals(PaneNode.Leaf(b), leafA.split(a, VERTICAL, b).removed(a))
    }

    @Test
    fun aDividerIsMovedByItsPath() {
        val root = three.withRatio(DividerPath(emptyList()), 0.3f) as PaneNode.Branch
        assertEquals(0.3f, root.ratio)

        val nested = three.withRatio(DividerPath(listOf(1)), 0.7f) as PaneNode.Branch
        assertEquals(0.5f, nested.ratio)
        assertEquals(0.7f, (nested.second as PaneNode.Branch).ratio)
    }

    @Test
    fun aPathThatLeadsNowhereChangesNothing() {
        assertEquals(three, three.withRatio(DividerPath(listOf(0)), 0.7f))
        assertEquals(three, three.withRatio(DividerPath(listOf(1, 1, 1)), 0.7f))
        assertEquals(leafA, leafA.withRatio(DividerPath(emptyList()), 0.7f))
    }

    @Test
    fun aPathStepIsZeroOrOne() {
        assertThrows(IllegalArgumentException::class.java) { DividerPath(listOf(2)) }
    }

    @Test
    fun aRatioIsStrictlyBetweenZeroAndOne() {
        assertThrows(IllegalArgumentException::class.java) {
            PaneNode.Branch(VERTICAL, 1f, leafA, PaneNode.Leaf(b))
        }
    }

    @Test
    fun swappingExchangesThePlacesOfTwoPanes() {
        assertEquals(listOf(c, b, a), three.swapped(a, c).leaves())
        assertEquals(three, three.swapped(a, c).swapped(a, c))
    }

    @Test
    fun theStoredFormKeepsTheShapeAndTheProportionsButNotTheSessions() {
        val stored = three.withRatio(DividerPath(listOf(1)), 0.25f).toLayoutNode()

        assertEquals(
            LayoutNode.Split(
                VERTICAL,
                0.5f,
                LayoutNode.Pane(),
                LayoutNode.Split(HORIZONTAL, 0.25f, LayoutNode.Pane(), LayoutNode.Pane())
            ),
            stored
        )
    }

    @Test
    fun aStoredLayoutComesBackWithNewSessionsInOrder() {
        val ids = generateSequence(10) { it + 1 }.map(::SessionId).iterator()

        val tree = paneNodeOf(three.toLayoutNode()) { ids.next() }

        assertEquals(listOf(SessionId(10), SessionId(11), SessionId(12)), tree.leaves())
        assertEquals(three.toLayoutNode(), tree.toLayoutNode())
    }

    @Test
    fun theStoredFormSurvivesTheJsonRoundTrip() {
        val stored = three.withRatio(DividerPath(listOf(1)), 0.25f).toLayoutNode()

        assertEquals(stored, LayoutCodec.decode(LayoutCodec.encode(stored)))
    }

    @Test
    fun snappingKeepsTheRatioAwayFromTheEndsAndFindsTheMiddle() {
        assertEquals(0.5f, snapRatio(0.51f, 0.1f))
        assertEquals(0.1f, snapRatio(0.01f, 0.1f))
        assertEquals(0.9f, snapRatio(0.99f, 0.1f))
        assertEquals(0.3f, snapRatio(0.3f, 0.1f))
        // A minimum that cannot be met on both sides falls back to the middle.
        assertEquals(0.5f, snapRatio(0.2f, 0.9f))
    }
}
