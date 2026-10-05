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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.Divider
import com.qtekfun.ultimateterminal.domain.session.FocusDirection
import com.qtekfun.ultimateterminal.domain.session.PaneRect
import com.qtekfun.ultimateterminal.domain.session.PaneScene
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.neighbour
import com.qtekfun.ultimateterminal.terminal.TerminalInputView
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalSessionHost
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosContextMenu
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosIcon
import com.qtekfun.ultimateterminal.ui.ios.IosMenuItem
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** The thin bar between two panes, and the touch target around it (the 48 dp of accessibility). */
private val DividerThickness = 4.dp
private val DividerTouchTarget = 48.dp

/**
 * The strip at the top of every pane of a split tab. The "⋯" button and the broadcast pill live in
 * it, so they never cover a line of text; the pty is told the pane less this strip ([belowHeader]).
 */
private val PaneHeaderHeight = 48.dp

/** The width the pane menu button takes at the end of the header strip. */
private val PaneMenuWidth = 48.dp
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
    screens: ScreenLinks,
    modifier: Modifier = Modifier,
    onTerminalUsed: () -> Unit = {}
) {
    val density = LocalDensity.current
    val dividerPx = with(density) { DividerThickness.roundToPx() }
    val isSplit by viewModel.panes.isSplit.collectAsStateWithLifecycle()
    val headerPx = with(density) { if (isSplit) PaneHeaderHeight.roundToPx() else 0 }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    ReportArea(viewModel, painter, areaSize, PaneStrips(dividerPx, headerPx))
    RefusalToasts(viewModel)
    CloseConfirmation(viewModel)

    val scene by viewModel.panes.scene.collectAsStateWithLifecycle()
    val focused by viewModel.panes.focused.collectAsStateWithLifecycle()
    // A pane's host is created after the scene names the pane: read again when one appears.
    val hostChanges by viewModel.hostChanges.collectAsStateWithLifecycle()
    val broadcast by viewModel.broadcastView.collectAsStateWithLifecycle()
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
                    outline = when {
                        broadcast.emitting && box.id in broadcast.targets -> PaneOutline.Broadcast
                        isSplit && hasKeyboard -> PaneOutline.Focus
                        else -> PaneOutline.None
                    },
                    header = if (isSplit) PaneHeaderHeight else 0.dp
                ) {
                    if (hasKeyboard) {
                        TerminalCanvas(viewModel, painter, onTap = {
                            inputView[0]?.showKeyboard()
                            onTerminalUsed()
                        })
                    } else {
                        val host = remember(box.id, hostChanges) { viewModel.hostOf(box.id) }
                        InactivePane(host, painter) {
                            viewModel.panes.focusPane(box.id)
                            inputView[0]?.showKeyboard()
                            onTerminalUsed()
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
        FocusedControls(viewModel, inputView, current, focused, screens)
        if (broadcast.emitting) BroadcastPill(current, broadcast.targets.size, viewModel)
    }
}

/**
 * The red broadcast pill, in the header strip of the first pane (D-FIX-6), left of its menu button:
 * over no terminal text, and the strip is as tall as its 48 dp touch target.
 */
@Composable
private fun BroadcastPill(scene: PaneScene, paneCount: Int, viewModel: TerminalViewModel) {
    val first = scene.panes.firstOrNull()?.rect ?: return
    val width = with(LocalDensity.current) { first.width.toDp() } - PaneMenuWidth
    BroadcastIndicator(
        paneCount = paneCount,
        onStop = viewModel.broadcast::stop,
        modifier = Modifier
            .offset { IntOffset(first.left, first.top) }
            .width(width.coerceAtLeast(0.dp))
    )
}

/** What sits over the pane that has the keyboard: the keyboard view, its buttons and its menu. */
@Composable
private fun FocusedControls(
    viewModel: TerminalViewModel,
    inputView: Array<TerminalInputView?>,
    scene: PaneScene,
    focused: SessionId?,
    screens: ScreenLinks
) {
    val rect = focused?.let { scene.rectOf(it) } ?: return
    val isSplit by viewModel.panes.isSplit.collectAsStateWithLifecycle()
    // A lone pane has nothing to swap, zoom or close, and its split lives in the tab bar's "+"
    // menu. In a split the button sits in the header strip, above the text and not over it.
    val header = if (isSplit) PaneHeaderHeight else 0.dp
    PanePlacement(rect, description = null, outline = PaneOutline.None, header = 0.dp) {
        Box(Modifier.padding(top = header)) { TerminalOverlays(viewModel, inputView) }
        if (isSplit) PaneMenu(viewModel, scene, focused, screens, Modifier.align(Alignment.TopEnd))
    }
}

/** Reports the size of the area, which the pane layout and the ptys follow. */
private fun Modifier.onSizeChangedTo(onSize: (IntSize) -> Unit): Modifier =
    this.onSizeChanged(onSize)

/** The outline of a pane: the one that has the keyboard, or one that what is typed also reaches. */
private enum class PaneOutline { None, Focus, Broadcast }

/** A box over [rect], clipped to it, with the outline of the pane that has the keyboard. */
@Composable
private fun PanePlacement(
    rect: PaneRect,
    description: String?,
    outline: PaneOutline,
    header: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val size = with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) }
    var box = Modifier
        .offset { IntOffset(rect.left, rect.top) }
        .size(size.width, size.height)
        .clipToBounds()
    if (description != null) box = box.semantics { contentDescription = description }
    when (outline) {
        PaneOutline.Focus -> box = box.border(FocusBorder, currentChrome().accent)
        PaneOutline.Broadcast -> box = box.border(FocusBorder, IosTheme.colors.destructive)
        PaneOutline.None -> Unit
    }
    Box(box) {
        Box(Modifier.fillMaxSize().padding(top = header), content = content)
    }
}

/** A pane that does not have the keyboard: its shell, live, and a tap to give it the keyboard. */
@Composable
private fun InactivePane(host: TerminalSessionHost?, painter: TerminalPainter, onTap: () -> Unit) {
    val frame by (host?.frame ?: NoFrames).collectAsStateWithLifecycle()
    val showKeyboard = stringResource(R.string.terminal_show_keyboard)
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics {
                onClick(label = showKeyboard) {
                    onTap()
                    true
                }
            }
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
    val backLabel = stringResource(R.string.pane_divider_back)
    val forwardLabel = stringResource(R.string.pane_divider_forward)
    // Dragging is the only touch way to resize, so a screen reader gets two steps as actions.
    val center = (if (vertical) width else height) / 2f
    Box(
        Modifier
            .offset { IntOffset(left, top) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(backLabel) {
                        report(origin + center - touchPx)
                        true
                    },
                    CustomAccessibilityAction(forwardLabel) {
                        report(origin + center + touchPx)
                        true
                    }
                )
            }
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
                .background(currentChrome().outline)
        )
    }
}

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
        IosAlert(
            title = stringResource(R.string.pane_close_title),
            message = stringResource(R.string.pane_close_message),
            actions = listOf(
                IosAction(stringResource(R.string.tab_cancel), IosActionRole.CANCEL),
                IosAction(
                    stringResource(R.string.tab_close_confirm),
                    IosActionRole.DESTRUCTIVE,
                    viewModel.panes.closing::confirm
                )
            ),
            onDismiss = viewModel.panes.closing::dismiss
        )
    }
}
