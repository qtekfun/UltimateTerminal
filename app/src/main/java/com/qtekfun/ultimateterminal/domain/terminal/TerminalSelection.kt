// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/** A cell in emulator coordinates: [row] is negative inside the scrollback. */
data class CellPosition(val column: Int, val row: Int) : Comparable<CellPosition> {
    override fun compareTo(other: CellPosition): Int =
        compareValuesBy(this, other, CellPosition::row, CellPosition::column)
}

/** A stream selection between two cells, in any direction. */
data class TerminalSelection(val anchor: CellPosition, val focus: CellPosition) {
    val start: CellPosition get() = minOf(anchor, focus)
    val end: CellPosition get() = maxOf(anchor, focus)

    /** The selected columns of [row] (end inclusive), or null if the row is not selected. */
    fun columnsOn(row: Int, columns: Int): IntRange? {
        if (row < start.row || row > end.row) return null
        val first = if (row == start.row) start.column else 0
        val last = if (row == end.row) end.column else columns - 1
        return first..last
    }

    fun withFocus(position: CellPosition): TerminalSelection = copy(focus = position)
}
