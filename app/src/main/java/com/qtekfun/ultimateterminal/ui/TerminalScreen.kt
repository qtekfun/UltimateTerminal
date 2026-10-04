// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
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
import com.qtekfun.ultimateterminal.domain.session.TabBarPlacement
import com.qtekfun.ultimateterminal.domain.session.reserveForTabBar
import com.qtekfun.ultimateterminal.domain.session.tabBarPlacement
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import com.qtekfun.ultimateterminal.domain.terminal.extraKeysHeightPx
import com.qtekfun.ultimateterminal.domain.terminal.reserveBottom
import com.qtekfun.ultimateterminal.domain.terminal.terminalLayoutFor
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.terminal.TerminalInputView
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalTypefaces
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * The terminal: a Compose canvas that draws the emulator, plus an invisible view that takes the
 * keyboard. The window is drawn edge to edge; the terminal takes all of it except what the system
 * bars, the display cutout and the keyboard cover, and the pty is resized to that area (T04).
 *
 * Prototype (T03, T04). Drawing, gestures, keyboard input and resizing have not been validated on
 * a device yet (see DECISIONS.md).
 */
@Composable
fun TerminalScreen(
    scheme: TerminalColorScheme,
    initialFontSizeSp: Float,
    onFontSizeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = viewModel()
) {
    val density = LocalDensity.current
    // Before the layout effect below, so a shell that starts on the first layout has the colors.
    SchemeAndFontEffects(viewModel, scheme, initialFontSizeSp, onFontSizeChanged)
    val fontSizeSp by viewModel.fontSize.sizeSp.collectAsStateWithLifecycle()
    val extraKeys by viewModel.extraKeys.collectAsStateWithLifecycle()
    val sticky by viewModel.stickyModifiers.collectAsStateWithLifecycle()
    val painter = rememberTerminalPainter(fontSizeSp, scheme)
    val inputView = remember { arrayOfNulls<TerminalInputView>(1) }

    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val insets = coveredEdges()
    // The extra-keys row sits above the keyboard, so its height is not terminal area.
    val extraKeysPx = extraKeysHeightPx(extraKeys, with(density) { ExtraKeyRowHeight.roundToPx() })
    // The tab bar is a row on top of narrow windows and a column beside the terminal on wide ones;
    // either way the grid must not count the space it takes.
    val placement = tabBarPlacement(with(density) { windowSize.width.toDp().value.toInt() })
    val tabBarPx = with(density) {
        (if (placement == TabBarPlacement.Top) TabBarHeight else TabBarSideWidth).roundToPx()
    }
    LaunchedEffect(windowSize, insets, extraKeysPx, placement, tabBarPx, painter) {
        if (windowSize != IntSize.Zero) {
            viewModel.onLayoutChanged(
                terminalLayoutFor(
                    windowSize.width,
                    windowSize.height,
                    insets.reserveBottom(extraKeysPx).reserveForTabBar(placement, tabBarPx),
                    painter.cellWidth,
                    painter.cellHeight
                )
            )
        }
    }

    // The scheme's background fills the whole window, bars included; the content is padded by the
    // same insets the layout was computed with, so what is drawn is exactly what the pty is told.
    Box(
        modifier.fillMaxSize().background(Color(scheme.background)).onSizeChanged {
            windowSize = it
        }
    ) {
        val padded = Modifier.fillMaxSize().padding(insets.toPadding(density))
        val pane = @Composable { paneModifier: Modifier ->
            Column(paneModifier) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    TerminalCanvas(viewModel, painter, onTap = { inputView[0]?.showKeyboard() })
                    TerminalOverlays(viewModel, inputView)
                }
                if (extraKeys.visible) {
                    ExtraKeysRow(extraKeys, sticky, viewModel.keyboard::onExtraKey)
                }
            }
        }
        if (placement == TabBarPlacement.Top) {
            Column(padded) {
                TabBar(viewModel.tabs, placement, Modifier.fillMaxWidth().height(TabBarHeight))
                pane(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(padded) {
                TabBar(viewModel.tabs, placement, Modifier.fillMaxHeight().width(TabBarSideWidth))
                pane(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** The painter for the current font size and scheme; the fonts are loaded once. */
@Composable
private fun rememberTerminalPainter(
    fontSizeSp: Float,
    scheme: TerminalColorScheme
): TerminalPainter {
    val density = LocalDensity.current
    val context = LocalContext.current
    val typefaces = remember { TerminalTypefaces.load(context) }
    return remember(density, fontSizeSp, typefaces, scheme.selection) {
        TerminalPainter(typefaces, with(density) { fontSizeSp.sp.toPx() }, scheme.selection)
    }
}

/** Applies the stored colors and font size, and saves the font size when the user changes it. */
@OptIn(FlowPreview::class)
@Composable
private fun SchemeAndFontEffects(
    viewModel: TerminalViewModel,
    scheme: TerminalColorScheme,
    initialFontSizeSp: Float,
    onFontSizeChanged: (Float) -> Unit
) {
    LaunchedEffect(scheme) { viewModel.applyScheme(scheme) }
    LaunchedEffect(viewModel) {
        viewModel.fontSize.restore(initialFontSizeSp)
        // The first value is the one just restored (or the default): only user changes are saved.
        viewModel.fontSize.sizeSp.drop(1).debounce(FONT_SIZE_SAVE_DELAY_MILLIS)
            .collect(onFontSizeChanged)
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

/** How long the font size must stay put before it is saved: a pinch changes it many times. */
private const val FONT_SIZE_SAVE_DELAY_MILLIS = 500L

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
