// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/**
 * The part of a selection that is on screen, in screen cells: row 0 is the top visible row, and
 * both ends are inclusive. It is what the floating menu is placed against, the same way Android
 * places it against the visible part of a selection in a text view.
 */
data class VisibleSelection(
    val firstRow: Int,
    val lastRow: Int,
    val firstColumn: Int,
    val lastColumn: Int
)

/**
 * Clips the selection to a screen of [rows] by [columns] whose first row is [topRow] (in emulator
 * rows), or returns null when no selected cell is on it. A selection that covers more than one row
 * spans the whole width: its first row runs to the right edge and its last starts at the left one.
 */
fun TerminalSelection.visibleIn(topRow: Int, rows: Int, columns: Int): VisibleSelection? {
    val first = maxOf(start.row, topRow)
    val last = minOf(end.row, topRow + rows - 1)
    if (rows <= 0 || columns <= 0 || first > last) return null
    val lastColumn = columns - 1
    val span = if (first == last) columnsOn(first, columns) else null
    return VisibleSelection(
        firstRow = first - topRow,
        lastRow = last - topRow,
        firstColumn = span?.first?.coerceIn(0, lastColumn) ?: 0,
        lastColumn = span?.last?.coerceIn(0, lastColumn) ?: lastColumn
    )
}

/** The most lines a drag past the edge scrolls in one step. */
const val MAX_EDGE_SCROLL_LINES = 4

/**
 * How many lines to scroll while a finger stretches a selection near the top or the bottom of a
 * view [height] pixels tall: negative goes back into the history, 0 means the finger is clear of
 * both edges. Inside the [edge] band it is one line per step; past the view it speeds up, one more
 * line for every [edge] pixels, up to [MAX_EDGE_SCROLL_LINES].
 */
fun edgeScrollLines(y: Float, height: Float, edge: Float): Int {
    require(edge > 0f) { "edge must be positive" }
    val beyond = when {
        y < edge -> edge - y
        y > height - edge -> y - (height - edge)
        else -> return 0
    }
    val lines = (1 + (beyond / edge).toInt()).coerceAtMost(MAX_EDGE_SCROLL_LINES)
    return if (y < edge) -lines else lines
}
