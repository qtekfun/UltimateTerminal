// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/**
 * Scroll position as the emulator's "top row": 0 shows the live screen, and -N shows N lines of
 * scrollback above it. It is clamped to the lines that exist.
 */
fun clampTopRow(topRow: Int, transcriptRows: Int): Int =
    topRow.coerceIn(-transcriptRows.coerceAtLeast(0), 0)

/** Turns a stream of pixel drags into whole lines, carrying the remainder between calls. */
class ScrollAccumulator {
    private var remainderPx = 0f

    /** Returns the whole lines (positive: finger moved down) that [deltaPx] completes. */
    fun consume(deltaPx: Float, lineHeightPx: Float): Int {
        require(lineHeightPx > 0f) { "line height must be positive" }
        remainderPx += deltaPx
        val lines = (remainderPx / lineHeightPx).toInt()
        remainderPx -= lines * lineHeightPx
        return lines
    }

    fun reset() {
        remainderPx = 0f
    }
}
