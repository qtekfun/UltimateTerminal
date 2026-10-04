// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.Divider
import com.qtekfun.ultimateterminal.domain.session.FocusDirection
import com.qtekfun.ultimateterminal.domain.session.PaneArea
import com.qtekfun.ultimateterminal.domain.session.PaneRect
import com.qtekfun.ultimateterminal.domain.session.PaneScene
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.neighbour
import com.qtekfun.ultimateterminal.terminal.TerminalInputView
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalSessionHost
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/** The thin bar between two panes, and the touch target around it (the 48 dp of accessibility). */
private val DividerThickness = 4.dp
private val DividerTouchTarget = 48.dp
private val PaneMenuButtonSize = 48.dp
private val FocusBorder = 2.dp
private val NoFrames = MutableStateFlow(0)

/**
 * The panes of the active tab, laid out by the domain ([PaneScene]) and drawn here: the pane that
 * has the keyboard is the full terminal (scrolling, selection, pinch zoom), the others show their
 * shell live, and a tap on one gives it the keyboard. The pane that has the keyboard is outlined
 * only when the tab is split. Not validated on a device yet (see DECISIONS.md, T10).
 */
@Composable
fun TerminalPanes(
    viewModel: TerminalViewModel,
    painter: TerminalPainter,
    inputView: Array<TerminalInputView?>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dividerPx = with(density) { DividerThickness.roundToPx() }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(areaSize, painter, dividerPx) {
        if (areaSize != IntSize.Zero) {
            viewModel.panes.onArea(
                PaneArea(
                    areaSize.width,
                    areaSize.height,
                    painter.cellWidth,
                    painter.cellHeight,
                    dividerPx
                )
            )
        }
    }
    RefusalToasts(viewModel)
    CloseConfirmation(viewModel)

    val scene by viewModel.panes.scene.collectAsStateWithLifecycle()
    val focused by viewModel.panes.focused.collectAsStateWithLifecycle()
    val isSplit by viewModel.panes.isSplit.collectAsStateWithLifecycle()
    Box(modifier.onSizeChangedTo { areaSize = it }) {
        val current = scene ?: return@Box
        current.panes.forEachIndexed { index, box ->
            key(box.id) {
                val hasKeyboard = box.id == focused
                PanePlacement(
                    rect = box.rect,
                    description = stringResource(
                        R.string.pane_description,
                        index + 1,
                        current.panes.size
                    ),
                    outlined = isSplit && hasKeyboard
                ) {
                    if (hasKeyboard) {
                        TerminalCanvas(viewModel, painter, onTap = { inputView[0]?.showKeyboard() })
                    } else {
                        InactivePane(viewModel.hostOf(box.id), painter) {
                            viewModel.panes.focusPane(box.id)
                            inputView[0]?.showKeyboard()
                        }
                    }
                }
            }
        }
        current.dividers.forEach { divider ->
            key(divider.path) {
                DividerHandle(divider) { pointer -> viewModel.panes.dragDivider(divider, pointer) }
            }
        }
        FocusedControls(viewModel, inputView, current, focused)
    }
}

/** What sits over the pane that has the keyboard: the keyboard view, its buttons and its menu. */
@Composable
private fun FocusedControls(
    viewModel: TerminalViewModel,
    inputView: Array<TerminalInputView?>,
    scene: PaneScene,
    focused: SessionId?
) {
    val rect = focused?.let { scene.rectOf(it) } ?: return
    PanePlacement(rect, description = null, outlined = false) {
        TerminalOverlays(viewModel, inputView)
        PaneMenu(viewModel, scene, focused, Modifier.align(Alignment.TopStart))
    }
}

/** Reports the size of the area, which the pane layout and the ptys follow. */
private fun Modifier.onSizeChangedTo(onSize: (IntSize) -> Unit): Modifier =
    this.onSizeChanged(onSize)

/** A box over [rect], clipped to it, with the outline of the pane that has the keyboard. */
@Composable
private fun PanePlacement(
    rect: PaneRect,
    description: String?,
    outlined: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val size = with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) }
    var box = Modifier
        .offset { IntOffset(rect.left, rect.top) }
        .size(size.width, size.height)
        .clipToBounds()
    if (description != null) box = box.semantics { contentDescription = description }
    if (outlined) box = box.border(FocusBorder, MaterialTheme.colorScheme.primary)
    Box(box, content = content)
}

/** A pane that does not have the keyboard: its shell, live, and a tap to give it the keyboard. */
@Composable
private fun InactivePane(host: TerminalSessionHost?, painter: TerminalPainter, onTap: () -> Unit) {
    val frame by (host?.frame ?: NoFrames).collectAsStateWithLifecycle()
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(host) { detectTapGestures { onTap() } }
    ) {
        val emulator = host?.emulator
        if (frame >= 0 && emulator != null) {
            drawIntoCanvas { painter.draw(it.nativeCanvas, emulator, 0, null) }
        }
    }
}

/**
 * Dragging this bar moves the divider. Only the thin bar is drawn; the touch target around it is
 * [DividerTouchTarget] wide so a finger can find it. The pty is not resized on every move: the
 * controller sends the size once the drag settles.
 */
@Composable
private fun DividerHandle(divider: Divider, onDrag: (pointerPx: Float) -> Unit) {
    val density = LocalDensity.current
    val vertical = divider.orientation == SplitOrientation.VERTICAL
    val touchPx = with(density) { DividerTouchTarget.roundToPx() }
    val bar = divider.rect
    val grow = ((touchPx - if (vertical) bar.width else bar.height).coerceAtLeast(0)) / 2
    val left = bar.left - if (vertical) grow else 0
    val top = bar.top - if (vertical) 0 else grow
    val width = bar.width + if (vertical) 2 * grow else 0
    val height = bar.height + if (vertical) 0 else 2 * grow
    val origin by rememberUpdatedState(if (vertical) left else top)
    val report by rememberUpdatedState(onDrag)
    val description = stringResource(R.string.pane_divider)
    Box(
        Modifier
            .offset { IntOffset(left, top) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .semantics { contentDescription = description }
            .pointerInput(divider.path) {
                detectDragGestures { change, _ ->
                    change.consume()
                    report(origin + if (vertical) change.position.x else change.position.y)
                }
            }
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(with(density) { bar.width.toDp() }, with(density) { bar.height.toDp() })
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

/** The "⋮" button of the pane that has the keyboard: split, zoom, swap and close. */
@Composable
private fun PaneMenu(
    viewModel: TerminalViewModel,
    scene: PaneScene,
    focused: SessionId?,
    modifier: Modifier
) {
    val isSplit by viewModel.panes.isSplit.collectAsStateWithLifecycle()
    val isZoomed by viewModel.panes.isZoomed.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.pane_menu)
    Box(modifier) {
        Box(
            Modifier
                .size(PaneMenuButtonSize)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .semantics { contentDescription = description }
                .pointerInput(Unit) { detectTapGestures { open = true } },
            contentAlignment = Alignment.Center
        ) {
            Text("⋮", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        val close = { open = false }
        DropdownMenu(expanded = open, onDismissRequest = close) {
            MenuEntry(R.string.pane_split_right, close, viewModel.panes::splitVertical)
            MenuEntry(R.string.pane_split_down, close, viewModel.panes::splitHorizontal)
            if (isSplit) {
                val zoom = if (isZoomed) R.string.pane_unzoom else R.string.pane_zoom
                MenuEntry(zoom, close, viewModel.panes::toggleZoom)
                MenuEntry(R.string.pane_close, close, viewModel.panes::closePane)
                for ((direction, label) in SwapLabels) {
                    if (focused != null && scene.neighbour(focused, direction) != null) {
                        MenuEntry(label, close) { viewModel.panes.swap(direction) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: Int, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            close()
            action()
        }
    )
}

private val SwapLabels = listOf(
    FocusDirection.Left to R.string.pane_swap_left,
    FocusDirection.Right to R.string.pane_swap_right,
    FocusDirection.Up to R.string.pane_swap_up,
    FocusDirection.Down to R.string.pane_swap_down
)

/** A split that does not fit says so instead of doing nothing. */
@Composable
private fun RefusalToasts(viewModel: TerminalViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.panes.refusals.collect {
            Toast.makeText(context, R.string.pane_too_small, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Asks before closing a pane whose shell still runs. */
@Composable
private fun CloseConfirmation(viewModel: TerminalViewModel) {
    val pending by viewModel.panes.closing.pending.collectAsStateWithLifecycle()
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel.panes.closing::dismiss,
            title = { Text(stringResource(R.string.pane_close_title)) },
            text = { Text(stringResource(R.string.pane_close_message)) },
            confirmButton = {
                TextButton(onClick = viewModel.panes.closing::confirm) {
                    Text(stringResource(R.string.tab_close_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel.panes.closing::dismiss) {
                    Text(stringResource(R.string.tab_cancel))
                }
            }
        )
    }
}
