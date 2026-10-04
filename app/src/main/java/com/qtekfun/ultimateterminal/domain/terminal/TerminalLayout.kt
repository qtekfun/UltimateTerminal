// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/** Pixels taken from each edge of the window by something that must not be drawn under. */
data class EdgeInsets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    /**
     * The insets that cover every edge of both: where the status bar, the cutout and the keyboard
     * overlap, only the largest counts (the keyboard replaces the navigation bar, it does not add
     * to it).
     */
    infix fun union(other: EdgeInsets) = EdgeInsets(
        left = maxOf(left, other.left),
        top = maxOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom)
    )

    companion object {
        val NONE = EdgeInsets(0, 0, 0, 0)
    }
}

/** What the pty has to be told: the grid, and the size of a cell in pixels. */
data class TerminalLayout(val grid: GridSize, val cellWidthPx: Int, val cellHeightPx: Int)

/**
 * The terminal layout for a window of [windowWidthPx] x [windowHeightPx] whose edges are covered by
 * [insets] (system bars, display cutout and keyboard already combined with [EdgeInsets.union]).
 * The drawn area is exactly the window minus the insets, so the grid fills it with no unused band
 * beyond the partial cell that cannot be avoided.
 */
fun terminalLayoutFor(
    windowWidthPx: Int,
    windowHeightPx: Int,
    insets: EdgeInsets,
    cellWidthPx: Float,
    cellHeightPx: Int
): TerminalLayout {
    val width = (windowWidthPx - insets.left - insets.right).coerceAtLeast(0)
    val height = (windowHeightPx - insets.top - insets.bottom).coerceAtLeast(0)
    return TerminalLayout(
        grid = gridSizeFor(width, height, cellWidthPx, cellHeightPx),
        cellWidthPx = cellWidthPx.toInt(),
        cellHeightPx = cellHeightPx
    )
}

/**
 * Keeps the pty from being resized on every frame of a keyboard animation or a window drag, each
 * resize being a `SIGWINCH` that makes full-screen programs redraw. The first layout passes at
 * once (the shell starts at the right size); afterwards only the last one is delivered, once no
 * newer arrives for [debounceMillis]. Repeated equal layouts are dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<TerminalLayout>.settled(debounceMillis: Long): Flow<TerminalLayout> {
    require(debounceMillis >= 0) { "debounce must not be negative" }
    val source = distinctUntilChanged()
    return flow {
        var first = true
        emitAll(
            source.transformLatest { layout ->
                if (!first) delay(debounceMillis)
                first = false
                emit(layout)
            }
        )
    }
}
