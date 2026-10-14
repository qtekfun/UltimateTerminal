// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel

/** Everything the terminal's touch handling needs from the screen. */
@Suppress("LongParameterList")
internal class TerminalGestureInput(
    val viewModel: TerminalViewModel,
    val painter: TerminalPainter,
    val description: String,
    val showKeyboard: String,
    val onTap: () -> Unit,
    val cellAt: (Offset) -> CellPosition,
    val selection: () -> TerminalSelection?,
    val topRow: () -> Int
)

/**
 * Tap raises the keyboard, a vertical drag scrolls, a long press picks a word and dragging stretches
 * it, a pinch zooms, and a touch on a selection handle moves that end of the selection.
 */
internal fun Modifier.terminalGestures(input: TerminalGestureInput): Modifier {
    val viewModel = input.viewModel
    val painter = input.painter
    return this
        .semantics {
            contentDescription = input.description
            onClick(label = input.showKeyboard) {
                input.onTap()
                true
            }
        }
        .pointerInput(painter) {
            detectTapGestures(onTap = {
                viewModel.selection.clear()
                input.onTap()
            })
        }
        .pointerInput(painter) {
            detectVerticalDragGestures { _, dragAmount ->
                viewModel.scrollBy(dragAmount, painter.cellHeight.toFloat())
            }
        }
        .pointerInput(painter) {
            detectDragGesturesAfterLongPress(
                onDragStart = { viewModel.selection.selectWord(input.cellAt(it)) },
                onDrag = { change, _ ->
                    viewModel.selection.extend(input.cellAt(change.position))
                }
            )
        }
        // Next to last, so it sees the events first and can take a two-finger pinch.
        .pinchToZoom(viewModel.fontSize::pinch)
        // Last of all: a touch on a selection handle is theirs, anything else passes on.
        .selectionHandles(
            selection = input.selection,
            topRow = input.topRow,
            painter = painter,
            onMove = viewModel.selection::moveEnd
        )
}
