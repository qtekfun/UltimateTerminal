// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.gridSizeFor
import com.qtekfun.ultimateterminal.terminal.TerminalInputView
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel

private const val TEXT_SIZE_SP = 14

/**
 * The terminal: a Compose canvas that draws the emulator, plus an invisible view that takes the
 * keyboard. The grid size follows the area available (system bars and keyboard excluded), and the
 * pty is resized to match.
 *
 * Prototype (T03). Drawing, gestures and keyboard input have not been validated on a device yet
 * (see DECISIONS.md).
 */
@Composable
fun TerminalScreen(modifier: Modifier = Modifier, viewModel: TerminalViewModel = viewModel()) {
    val density = LocalDensity.current
    val painter = remember(density) {
        TerminalPainter(Typeface.MONOSPACE, with(density) { TEXT_SIZE_SP.sp.toPx() })
    }
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val exitStatus by viewModel.exitStatus.collectAsStateWithLifecycle()
    val inputView = remember { arrayOfNulls<TerminalInputView>(1) }

    Box(modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        TerminalCanvas(viewModel, painter, onTap = { inputView[0]?.showKeyboard() })

        AndroidView(
            factory = { context -> TerminalInputView(context).also { inputView[0] = it } },
            update = { it.sink = viewModel.keyboard },
            modifier = Modifier.size(1.dp)
        )
        LaunchedEffect(Unit) { inputView[0]?.showKeyboard() }

        if (selection != null) {
            TextButton(
                onClick = viewModel::copySelection,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Text(stringResource(R.string.terminal_copy))
            }
        }
        exitStatus?.let { status ->
            Button(onClick = viewModel::restart, modifier = Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.terminal_session_ended, status))
            }
        }
    }
}

/** Draws the screen and turns touches into scrolling, selection and a request for the keyboard. */
@Composable
private fun TerminalCanvas(
    viewModel: TerminalViewModel,
    painter: TerminalPainter,
    onTap: () -> Unit
) {
    val frame by viewModel.frame.collectAsStateWithLifecycle()
    val topRow by viewModel.topRow.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()

    fun cellAt(offset: Offset) = CellPosition(
        column = (offset.x / painter.cellWidth).toInt().coerceAtLeast(0),
        row = (offset.y / painter.cellHeight).toInt().coerceAtLeast(0) + topRow
    )

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val grid =
                    gridSizeFor(size.width, size.height, painter.cellWidth, painter.cellHeight)
                viewModel.onGridChanged(grid, painter.cellWidth.toInt(), painter.cellHeight)
            }
            .pointerInput(painter) {
                detectTapGestures(onTap = {
                    viewModel.clearSelection()
                    onTap()
                })
            }
            .pointerInput(painter) {
                detectVerticalDragGestures { _, dragAmount ->
                    viewModel.scrollBy(dragAmount, painter.cellHeight.toFloat())
                }
            }
            .pointerInput(painter) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { viewModel.startSelection(cellAt(it)) },
                    onDrag = { change, _ -> viewModel.extendSelection(cellAt(change.position)) }
                )
            }
    ) {
        // Reading the frame here makes only the drawing, not the composition, depend on it.
        val emulator = viewModel.emulator
        if (frame >= 0 && emulator != null) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, emulator, topRow, selection) }
        }
    }
}
