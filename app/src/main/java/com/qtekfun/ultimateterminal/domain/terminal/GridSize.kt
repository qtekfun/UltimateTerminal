// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/** Size of the terminal in character cells. */
data class GridSize(val columns: Int, val rows: Int) {
    companion object {
        /** The emulator rejects anything smaller than 2x2. */
        const val MIN_COLUMNS = 2
        const val MIN_ROWS = 2
    }
}

/**
 * How many whole cells fit in an area of [widthPx] x [heightPx] pixels. Partial cells are not
 * counted, so the grid never overflows the area; the pty is told exactly this size.
 */
fun gridSizeFor(widthPx: Int, heightPx: Int, cellWidthPx: Float, cellHeightPx: Int): GridSize {
    require(cellWidthPx > 0f && cellHeightPx > 0) { "cell size must be positive" }
    return GridSize(
        columns = (widthPx / cellWidthPx).toInt().coerceAtLeast(GridSize.MIN_COLUMNS),
        rows = (heightPx / cellHeightPx).coerceAtLeast(GridSize.MIN_ROWS)
    )
}
