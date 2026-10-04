// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.terminal.terminalLayoutFor
import kotlin.math.roundToInt

/** A rectangle in pixels. */
data class PaneRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/** Where one pane is drawn. */
data class PaneBox(val id: SessionId, val rect: PaneRect)

/**
 * A draggable divider. [rect] is the thin bar itself; [span] is the whole area of the split it
 * belongs to, which is what a pointer position is measured against.
 */
data class Divider(
    val path: DividerPath,
    val orientation: SplitOrientation,
    val rect: PaneRect,
    val span: PaneRect
)

/** Where every pane and divider of a tab are, for one window area. */
data class PaneScene(val panes: List<PaneBox>, val dividers: List<Divider>) {
    fun rectOf(id: SessionId): PaneRect? = panes.firstOrNull { it.id == id }?.rect
}

enum class FocusDirection { Left, Right, Up, Down }

/** What [paneScene] collects while it walks the tree. */
private class Placement(val dividerPx: Int) {
    val panes = mutableListOf<PaneBox>()
    val dividers = mutableListOf<Divider>()
}

/**
 * Lays [root] out inside [bounds], leaving [dividerPx] between the two halves of each split. The
 * panes cover the area exactly: what a pane gets is what its pty is told (see [paneLayouts]).
 */
fun paneScene(root: PaneNode, bounds: PaneRect, dividerPx: Int): PaneScene {
    val placement = Placement(dividerPx.coerceAtLeast(0))
    place(root, bounds, DividerPath(emptyList()), placement)
    return PaneScene(placement.panes, placement.dividers)
}

private fun place(node: PaneNode, area: PaneRect, path: DividerPath, out: Placement) {
    when (node) {
        is PaneNode.Leaf -> out.panes += PaneBox(node.id, area)
        is PaneNode.Branch -> placeBranch(node, area, path, out)
    }
}

private fun placeBranch(node: PaneNode.Branch, area: PaneRect, path: DividerPath, out: Placement) {
    val dividerPx = out.dividerPx
    val sideBySide = node.orientation == SplitOrientation.VERTICAL
    val length = if (sideBySide) area.width else area.height
    val room = (length - dividerPx).coerceAtLeast(0)
    val firstLength = (room * node.ratio).roundToInt()
    val (firstArea, bar, secondArea) = if (sideBySide) {
        Triple(
            area.copy(width = firstLength),
            area.copy(left = area.left + firstLength, width = dividerPx.coerceAtMost(length)),
            area.copy(left = area.left + firstLength + dividerPx, width = room - firstLength)
        )
    } else {
        Triple(
            area.copy(height = firstLength),
            area.copy(top = area.top + firstLength, height = dividerPx.coerceAtMost(length)),
            area.copy(top = area.top + firstLength + dividerPx, height = room - firstLength)
        )
    }
    out.dividers += Divider(path, node.orientation, bar, area)
    place(node.first, firstArea, path.child(0), out)
    place(node.second, secondArea, path.child(1), out)
}

/** The size, in cells, each pane's pty has to be told: the pane's area, as T04 does for a window. */
fun paneLayouts(
    scene: PaneScene,
    cellWidthPx: Float,
    cellHeightPx: Int
): Map<SessionId, TerminalLayout> = scene.panes.associate { (id, rect) ->
    id to terminalLayoutFor(rect.width, rect.height, EdgeInsets.NONE, cellWidthPx, cellHeightPx)
}

/**
 * The share a pointer at [pointerPx] (along the axis of the split, in the same coordinates as the
 * divider's span) gives the first half, kept at least [minPanePx] from each end and snapped to the
 * middle when close to it.
 */
fun ratioForPointer(divider: Divider, pointerPx: Float, dividerPx: Int, minPanePx: Int): Float {
    val sideBySide = divider.orientation == SplitOrientation.VERTICAL
    val start = if (sideBySide) divider.span.left else divider.span.top
    val length = if (sideBySide) divider.span.width else divider.span.height
    val room = (length - dividerPx).coerceAtLeast(1)
    val raw = (pointerPx - start - dividerPx / 2f) / room
    return snapRatio(raw, minPanePx.toFloat() / room)
}
