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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import com.qtekfun.ultimateterminal.domain.terminal.extraKeysHeightPx
import com.qtekfun.ultimateterminal.domain.terminal.reserveBottom
import com.qtekfun.ultimateterminal.domain.terminal.terminalLayoutFor
import com.qtekfun.ultimateterminal.terminal.TerminalInputView
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel

/**
 * The terminal: a Compose canvas that draws the emulator, plus an invisible view that takes the
 * keyboard. The window is drawn edge to edge; the terminal takes all of it except what the system
 * bars, the display cutout and the keyboard cover, and the pty is resized to that area (T04).
 *
 * Prototype (T03, T04). Drawing, gestures, keyboard input and resizing have not been validated on
 * a device yet (see DECISIONS.md).
 */
@Composable
fun TerminalScreen(modifier: Modifier = Modifier, viewModel: TerminalViewModel = viewModel()) {
    val density = LocalDensity.current
    val fontSizeSp by viewModel.fontSize.sizeSp.collectAsStateWithLifecycle()
    val extraKeys by viewModel.extraKeys.collectAsStateWithLifecycle()
    val sticky by viewModel.stickyModifiers.collectAsStateWithLifecycle()
    val painter = remember(density, fontSizeSp) {
        TerminalPainter(Typeface.MONOSPACE, with(density) { fontSizeSp.sp.toPx() })
    }
    val inputView = remember { arrayOfNulls<TerminalInputView>(1) }

    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val insets = coveredEdges()
    // The extra-keys row sits above the keyboard, so its height is not terminal area.
    val extraKeysPx = extraKeysHeightPx(extraKeys, with(density) { ExtraKeyRowHeight.roundToPx() })
    LaunchedEffect(windowSize, insets, extraKeysPx, painter) {
        if (windowSize != IntSize.Zero) {
            viewModel.onLayoutChanged(
                terminalLayoutFor(
                    windowSize.width,
                    windowSize.height,
                    insets.reserveBottom(extraKeysPx),
                    painter.cellWidth,
                    painter.cellHeight
                )
            )
        }
    }

    // The black background fills the whole window, bars included; the content is padded by the
    // same insets the layout was computed with, so what is drawn is exactly what the pty is told.
    Box(modifier.fillMaxSize().background(Color.Black).onSizeChanged { windowSize = it }) {
        Column(Modifier.fillMaxSize().padding(insets.toPadding(density))) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TerminalCanvas(viewModel, painter, onTap = { inputView[0]?.showKeyboard() })
                TerminalOverlays(viewModel, inputView)
            }
            if (extraKeys.visible) {
                ExtraKeysRow(extraKeys, sticky, viewModel.keyboard::onExtraKey)
            }
        }
    }
}

@Composable
private fun TerminalOverlays(viewModel: TerminalViewModel, inputView: Array<TerminalInputView?>) {
    val selection by viewModel.selection.selection.collectAsStateWithLifecycle()
    val exitStatus by viewModel.exitStatus.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> TerminalInputView(context).also { inputView[0] = it } },
            update = { it.sink = viewModel.keyboard },
            modifier = Modifier.size(1.dp)
        )
        LaunchedEffect(Unit) { inputView[0]?.showKeyboard() }

        if (selection != null) {
            TextButton(
                onClick = viewModel.selection::copy,
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
    val selection by viewModel.selection.selection.collectAsStateWithLifecycle()
    val onPinch = remember(viewModel) { viewModel.fontSize::pinch }

    fun cellAt(offset: Offset) = CellPosition(
        column = (offset.x / painter.cellWidth).toInt().coerceAtLeast(0),
        row = (offset.y / painter.cellHeight).toInt().coerceAtLeast(0) + topRow
    )

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(painter) {
                detectTapGestures(onTap = {
                    viewModel.selection.clear()
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
                    onDragStart = { viewModel.selection.start(cellAt(it)) },
                    onDrag = { change, _ -> viewModel.selection.extend(cellAt(change.position)) }
                )
            }
            // Last, so it sees the events first and can take a two-finger pinch for itself.
            .pinchToZoom(onPinch)
    ) {
        // Reading the frame here makes only the drawing, not the composition, depend on it.
        val emulator = viewModel.emulator
        if (frame >= 0 && emulator != null) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, emulator, topRow, selection) }
        }
    }
}

/** What the system bars, the display cutout and the keyboard cover, combined edge by edge. */
@Composable
private fun coveredEdges(): EdgeInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return WindowInsets.systemBars.toEdgeInsets(density, direction) union
        WindowInsets.displayCutout.toEdgeInsets(density, direction) union
        WindowInsets.ime.toEdgeInsets(density, direction)
}

private fun WindowInsets.toEdgeInsets(density: Density, direction: LayoutDirection) = EdgeInsets(
    left = getLeft(density, direction),
    top = getTop(density),
    right = getRight(density, direction),
    bottom = getBottom(density)
)

private fun EdgeInsets.toPadding(density: Density) = with(density) {
    PaddingValues(
        start = left.toDp(),
        top = top.toDp(),
        end = right.toDp(),
        bottom = bottom.toDp()
    )
}
