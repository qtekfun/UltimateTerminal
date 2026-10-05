// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation

/**
 * The pane that [direction] leads to from the pane [from]: among those on that side whose extent
 * overlaps it, the nearest, and of those the one that overlaps most. Null at the edge.
 */
fun PaneScene.neighbour(from: SessionId, direction: FocusDirection): SessionId? {
    val origin = rectOf(from) ?: return null
    val horizontal = direction == FocusDirection.Left || direction == FocusDirection.Right
    return panes
        .filter { it.id != from }
        .mapNotNull { candidate ->
            val gap = gapTowards(origin, candidate.rect, direction)
            val overlap = overlap(origin, candidate.rect, horizontal)
            if (gap == null || overlap <= 0) null else Triple(candidate.id, gap, overlap)
        }
        .minWithOrNull(
            compareBy<Triple<SessionId, Int, Int>> {
                it.second
            }.thenByDescending { it.third }
        )
        ?.first
}

/** How far [other] is from [origin] in [direction], or null if it is not on that side. */
private fun gapTowards(origin: PaneRect, other: PaneRect, direction: FocusDirection): Int? {
    val gap = when (direction) {
        FocusDirection.Left -> origin.left - other.right
        FocusDirection.Right -> other.left - origin.right
        FocusDirection.Up -> origin.top - other.bottom
        FocusDirection.Down -> other.top - origin.bottom
    }
    return gap.takeIf { it >= 0 }
}

/** How much the two rectangles share along the axis across the movement. */
private fun overlap(a: PaneRect, b: PaneRect, horizontalMove: Boolean): Int = if (horizontalMove) {
    minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
} else {
    minOf(a.right, b.right) - maxOf(a.left, b.left)
}

/** The smallest a pane may get, in cells: enough for a prompt and a few lines. */
const val MIN_PANE_COLUMNS = 20
const val MIN_PANE_ROWS = 4

/**
 * Whether a pane of [rect] can be split along [orientation] and leave both halves at least
 * [MIN_PANE_COLUMNS] x [MIN_PANE_ROWS] cells of text, counting the divider and the [headerPx]
 * strip each pane of a split tab carries at its top.
 */
fun canSplit(
    rect: PaneRect,
    orientation: SplitOrientation,
    cellWidthPx: Float,
    cellHeightPx: Int,
    dividerPx: Int,
    headerPx: Int = 0
): Boolean = when (orientation) {
    SplitOrientation.VERTICAL ->
        rect.width - dividerPx >= 2 * MIN_PANE_COLUMNS * cellWidthPx &&
            rect.height - headerPx >= MIN_PANE_ROWS * cellHeightPx

    SplitOrientation.HORIZONTAL ->
        rect.height - dividerPx >= 2 * (MIN_PANE_ROWS * cellHeightPx + headerPx)
}
