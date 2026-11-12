// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.domain.terminal.edgeScrollLines
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The narrowest band at each edge that scrolls while a selection is stretched. */
private val MinEdge = 24.dp

/** How often the view moves while the finger stays at an edge. */
private const val EDGE_SCROLL_STEP_MILLIS = 50L

/**
 * Keeps scrolling while a finger that stretches a selection rests near the top or the bottom of the
 * view, and moves the selection end with it ([onStep] receives the last finger position). Call
 * [follow] on every movement and [stop] when the finger lifts.
 */
internal class EdgeAutoScroll(
    private val scope: CoroutineScope,
    private val view: PointerInputScope,
    painter: TerminalPainter,
    private val scrollLines: (Int) -> Unit,
    private val onStep: (Offset) -> Unit
) {
    private val edge = maxOf(painter.cellHeight.toFloat(), with(view) { MinEdge.toPx() })
    private var lastPosition = Offset.Zero
    private var job: Job? = null

    private fun linesAt(position: Offset) =
        edgeScrollLines(position.y, view.size.height.toFloat(), edge)

    fun follow(position: Offset) {
        lastPosition = position
        if (job?.isActive == true || linesAt(position) == 0) return
        job = scope.launch {
            while (isActive) {
                val lines = linesAt(lastPosition)
                if (lines == 0) break
                scrollLines(lines)
                onStep(lastPosition)
                delay(EDGE_SCROLL_STEP_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
