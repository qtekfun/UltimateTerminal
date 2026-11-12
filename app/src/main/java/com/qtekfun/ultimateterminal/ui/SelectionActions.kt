// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import kotlinx.coroutines.coroutineScope

/** What the floating menu over a selection can do; [dismiss] runs when the system closes it (back). */
internal class SelectionActions(
    val copy: () -> Unit,
    val paste: () -> Unit,
    val selectAll: () -> Unit,
    val selectLine: () -> Unit,
    val dismiss: () -> Unit
)

internal val HandleRadius = 9.dp
private val HandleReach = 28.dp

private fun handlePoints(
    selection: TerminalSelection,
    topRow: Int,
    painter: TerminalPainter
): Pair<Offset, Offset> {
    val cellW = painter.cellWidth
    val cellH = painter.cellHeight.toFloat()
    val start = Offset(
        selection.start.column * cellW,
        (selection.start.row - topRow + 1) * cellH
    )
    val end = Offset(
        (selection.end.column + 1) * cellW,
        (selection.end.row - topRow + 1) * cellH
    )
    return start to end
}

/** Draws a grab handle under each end of the selection. */
internal fun DrawScope.drawSelectionHandles(
    selection: TerminalSelection,
    topRow: Int,
    painter: TerminalPainter,
    color: Color
) {
    val (start, end) = handlePoints(selection, topRow, painter)
    val radius = HandleRadius.toPx()
    drawCircle(color, radius, start.copy(y = start.y + radius))
    drawCircle(color, radius, end.copy(y = end.y + radius))
}

/**
 * Lets a finger drag either handle. It runs first in the pointer chain and only takes the gesture
 * when the touch lands on a handle, so scrolling, selecting and pinching are left alone otherwise.
 * Dragging a handle to the top or bottom edge scrolls the view, so a selection can grow past one
 * screen, as it does in Android's text views.
 */
internal fun Modifier.selectionHandles(
    selection: () -> TerminalSelection?,
    topRow: () -> Int,
    painter: TerminalPainter,
    scrollLines: (Int) -> Unit,
    onMove: (movingStart: Boolean, CellPosition) -> Unit
): Modifier = pointerInput(painter) {
    val reach = HandleReach.toPx()
    val below = painter.cellHeight / 2f

    // The handle hangs below its cell, so the cell is the one just above the finger.
    fun cellUnder(position: Offset) = CellPosition(
        column = (position.x / painter.cellWidth).toInt().coerceAtLeast(0),
        row = ((position.y - below) / painter.cellHeight).toInt().coerceAtLeast(0) + topRow()
    )
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val current = selection() ?: return@awaitEachGesture
            val (start, end) = handlePoints(current, topRow(), painter)
            val toStart = (down.position - start.copy(y = start.y + below)).getDistance()
            val toEnd = (down.position - end.copy(y = end.y + below)).getDistance()
            val movingStart = toStart <= toEnd
            if (minOf(toStart, toEnd) > reach) return@awaitEachGesture
            down.consume()
            val autoScroll =
                EdgeAutoScroll(this@coroutineScope, this@pointerInput, painter, scrollLines) {
                    onMove(movingStart, cellUnder(it))
                }
            try {
                do {
                    val change =
                        awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: break
                    change.consume()
                    onMove(movingStart, cellUnder(change.position))
                    autoScroll.follow(change.position)
                } while (change.pressed)
            } finally {
                autoScroll.stop()
            }
        }
    }
}
