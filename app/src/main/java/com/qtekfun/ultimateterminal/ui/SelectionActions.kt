// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** What the floating bar over a selection can do. */
internal class SelectionActions(
    val copy: () -> Unit,
    val paste: () -> Unit,
    val selectAll: () -> Unit,
    val selectLine: () -> Unit
)

private val BarHeight = 44.dp
private val BarGap = 8.dp
private val HandleRadius = 9.dp
private val HandleReach = 28.dp

/**
 * A compact bar next to the selection (above its first row, or below its last one when there is no
 * room above) with copy, paste, select all and select line. It replaces two big fixed buttons that
 * sat over the text in the corner of the screen.
 */
@Composable
internal fun SelectionToolbar(
    selection: TerminalSelection,
    topRow: Int,
    painter: TerminalPainter,
    actions: SelectionActions,
    modifier: Modifier = Modifier
) {
    val colors = IosTheme.colors
    val shape = RoundedCornerShape(BarHeight / 2)
    Row(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val barHeight = BarHeight.roundToPx()
                val gap = BarGap.roundToPx()
                val cell = painter.cellHeight
                val startY = (selection.start.row - topRow) * cell
                val endY = (selection.end.row - topRow + 1) * cell
                val above = startY - barHeight - gap
                val y = if (above >= 0) above else endY + gap + HandleRadius.roundToPx() * 2
                val x = (selection.start.column * painter.cellWidth).toInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(x, y.coerceAtLeast(0))
                }
            }
            .background(colors.cell, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarAction(R.string.terminal_copy, null, colors.tint, actions.copy)
        BarAction(R.string.terminal_paste, null, colors.tint, actions.paste)
        BarAction(
            R.string.terminal_select_all,
            R.string.terminal_select_all_description,
            colors.tint,
            actions.selectAll
        )
        BarAction(R.string.terminal_select_line, null, colors.tint, actions.selectLine)
    }
}

@Composable
private fun BarAction(label: Int, description: Int?, color: Color, onClick: () -> Unit) {
    val spoken = description?.let { stringResource(it) }
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = BarHeight)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                spoken?.let { contentDescription = it }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(label), color = color, fontSize = 14.sp)
    }
}

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
 */
internal fun Modifier.selectionHandles(
    selection: () -> TerminalSelection?,
    topRow: () -> Int,
    painter: TerminalPainter,
    onMove: (movingStart: Boolean, CellPosition) -> Unit
): Modifier = pointerInput(painter) {
    val reach = HandleReach.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val current = selection() ?: return@awaitEachGesture
        val (start, end) = handlePoints(current, topRow(), painter)
        val below = painter.cellHeight / 2f
        val toStart = (down.position - start.copy(y = start.y + below)).getDistance()
        val toEnd = (down.position - end.copy(y = end.y + below)).getDistance()
        val movingStart = toStart <= toEnd
        if (minOf(toStart, toEnd) > reach) return@awaitEachGesture
        down.consume()
        do {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: break
            change.consume()
            // The handle hangs below its cell, so the cell is the one just above the finger.
            val cell = CellPosition(
                column = (change.position.x / painter.cellWidth).toInt().coerceAtLeast(0),
                row = ((change.position.y - below) / painter.cellHeight).toInt()
                    .coerceAtLeast(0) + topRow()
            )
            onMove(movingStart, cell)
        } while (change.pressed)
    }
}
