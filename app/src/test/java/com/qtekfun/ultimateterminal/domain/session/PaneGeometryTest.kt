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

class PaneGeometryTest {
    private val a = SessionId(1)
    private val b = SessionId(2)
    private val c = SessionId(3)
    private val d = SessionId(4)
    private val bounds = PaneRect(0, 0, 1001, 600)

    /** a | b, side by side. */
    private val sideBySide = PaneNode.Leaf(a).split(a, VERTICAL, b)

    /** a b / c d, two rows of two. */
    private val grid: PaneNode = PaneNode.Leaf(a)
        .split(a, HORIZONTAL, c)
        .split(a, VERTICAL, b)
        .split(c, VERTICAL, d)

    @Test
    fun aLonePaneTakesTheWholeArea() {
        val scene = paneScene(PaneNode.Leaf(a), bounds, dividerPx = 4)

        assertEquals(listOf(PaneBox(a, bounds)), scene.panes)
        assertTrue(scene.dividers.isEmpty())
    }

    @Test
    fun twoPanesShareTheAreaAndTheDividerSitsBetweenThem() {
        val scene = paneScene(sideBySide, bounds, dividerPx = 1)

        assertEquals(PaneRect(0, 0, 500, 600), scene.rectOf(a))
        assertEquals(PaneRect(501, 0, 500, 600), scene.rectOf(b))
        assertEquals(PaneRect(500, 0, 1, 600), scene.dividers.single().rect)
        assertEquals(bounds, scene.dividers.single().span)
    }

    @Test
    fun aHorizontalSplitPutsTheSecondPaneBelow() {
        val scene = paneScene(PaneNode.Leaf(a).split(a, HORIZONTAL, b), bounds, dividerPx = 0)

        assertEquals(PaneRect(0, 0, 1001, 300), scene.rectOf(a))
        assertEquals(PaneRect(0, 300, 1001, 300), scene.rectOf(b))
    }

    @Test
    fun theRatioDecidesTheShare() {
        val tree = sideBySide.withRatio(DividerPath(emptyList()), 0.25f)

        val scene = paneScene(tree, PaneRect(0, 0, 804, 100), dividerPx = 4)

        assertEquals(200, scene.rectOf(a)?.width)
        assertEquals(600, scene.rectOf(b)?.width)
        assertEquals(204, scene.rectOf(b)?.left)
    }

    @Test
    fun thePanesAndTheDividersCoverTheAreaExactly() {
        for (ratio in listOf(0.1f, 0.33f, 0.5f, 0.77f, 0.9f)) {
            val tree = grid.withRatio(DividerPath(emptyList()), ratio)
                .withRatio(DividerPath(listOf(0)), ratio)
            val area = PaneRect(10, 20, 997, 613)

            val scene = paneScene(tree, area, dividerPx = 3)

            val rows = scene.panes.groupBy { it.rect.top }.values
            for (row in rows) {
                val widths = row.sumOf { it.rect.width } + 3 * (row.size - 1)
                assertEquals(area.width, widths, "row at ratio $ratio")
            }
            assertEquals(
                area.height,
                scene.panes.filter { it.rect.left == area.left }
                    .sumOf { it.rect.height } + 3,
                "column at ratio $ratio"
            )
        }
    }

    @Test
    fun everyDividerCarriesThePathThatReachesItInTheTree() {
        val scene = paneScene(grid, bounds, dividerPx = 2)

        assertEquals(
            listOf(emptyList(), listOf(0), listOf(1)),
            scene.dividers.map { it.path.steps }
        )
    }

    @Test
    fun aTinyAreaNeverGivesNegativeSizes() {
        val scene = paneScene(grid, PaneRect(0, 0, 5, 3), dividerPx = 8)

        assertTrue(scene.panes.all { it.rect.width >= 0 && it.rect.height >= 0 })
    }

    @Test
    fun eachPtyIsToldTheGridOfItsPane() {
        val scene = paneScene(sideBySide, PaneRect(0, 0, 1001, 600), dividerPx = 1)

        val layouts = paneLayouts(scene, cellWidthPx = 10f, cellHeightPx = 20)

        assertEquals(50, layouts.getValue(a).grid.columns)
        assertEquals(30, layouts.getValue(a).grid.rows)
        assertEquals(50, layouts.getValue(b).grid.columns)
    }

    @Test
    fun draggingTheDividerGivesTheRatioUnderThePointer() {
        val divider = paneScene(
            sideBySide,
            PaneRect(0, 0, 1004, 600),
            dividerPx = 4
        ).dividers.single()

        // The middle of the first pane's right edge: 25% of the 1000 px of room.
        assertEquals(0.25f, ratioForPointer(divider, 252f, dividerPx = 4, minPanePx = 100))
    }

    @Test
    fun theRatioSnapsToTheMiddleAndRespectsTheMinimumSize() {
        val divider = paneScene(
            sideBySide,
            PaneRect(0, 0, 1004, 600),
            dividerPx = 4
        ).dividers.single()

        assertEquals(0.5f, ratioForPointer(divider, 510f, dividerPx = 4, minPanePx = 100))
        assertEquals(0.1f, ratioForPointer(divider, 3f, dividerPx = 4, minPanePx = 100))
        assertEquals(0.9f, ratioForPointer(divider, 1003f, dividerPx = 4, minPanePx = 100))
    }

    @Test
    fun theDividerOfAHorizontalSplitIsMeasuredAlongTheHeight() {
        val tree = PaneNode.Leaf(a).split(a, HORIZONTAL, b)
        val divider = paneScene(tree, PaneRect(0, 100, 500, 404), dividerPx = 4).dividers.single()

        assertEquals(0.75f, ratioForPointer(divider, 100f + 302f, dividerPx = 4, minPanePx = 40))
    }

    @Test
    fun focusMovesToTheNearestPaneOnThatSide() {
        val scene = paneScene(grid, bounds, dividerPx = 2)

        assertEquals(b, scene.neighbour(a, FocusDirection.Right))
        assertEquals(c, scene.neighbour(a, FocusDirection.Down))
        assertEquals(a, scene.neighbour(b, FocusDirection.Left))
        assertEquals(b, scene.neighbour(d, FocusDirection.Up))
        assertEquals(c, scene.neighbour(d, FocusDirection.Left))
    }

    @Test
    fun thereIsNoNeighbourAtTheEdgeOrForAnUnknownPane() {
        val scene = paneScene(grid, bounds, dividerPx = 2)

        assertNull(scene.neighbour(a, FocusDirection.Left))
        assertNull(scene.neighbour(a, FocusDirection.Up))
        assertNull(scene.neighbour(SessionId(99), FocusDirection.Right))
    }

    @Test
    fun withUnevenPanesTheOneThatOverlapsMostWins() {
        // a is tall on the left; on the right b is small on top and c is large below.
        val tree = PaneNode.Leaf(a).split(a, VERTICAL, b).split(b, HORIZONTAL, c)
            .withRatio(DividerPath(listOf(1)), 0.2f)
        val scene = paneScene(tree, bounds, dividerPx = 0)

        assertEquals(c, scene.neighbour(a, FocusDirection.Right))
        assertEquals(a, scene.neighbour(b, FocusDirection.Left))
        assertEquals(a, scene.neighbour(c, FocusDirection.Left))
    }

    @Test
    fun aSplitNeedsRoomForTwoUsablePanes() {
        // 20 columns of 10 px on each side of a 4 px divider: 404 px, and 4 rows of 20 px: 164 px.
        assertFalse(canSplit(PaneRect(0, 0, 403, 300), VERTICAL, 10f, 20, dividerPx = 4))
        assertTrue(canSplit(PaneRect(0, 0, 404, 300), VERTICAL, 10f, 20, dividerPx = 4))
        assertTrue(canSplit(PaneRect(0, 0, 404, 164), HORIZONTAL, 10f, 20, dividerPx = 4))
        assertFalse(canSplit(PaneRect(0, 0, 404, 163), HORIZONTAL, 10f, 20, dividerPx = 4))
    }
}
