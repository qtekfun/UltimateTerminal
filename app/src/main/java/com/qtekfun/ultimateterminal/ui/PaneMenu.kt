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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.broadcast.BroadcastMode
import com.qtekfun.ultimateterminal.domain.broadcast.BroadcastView
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
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosContextMenu
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosIcon
import com.qtekfun.ultimateterminal.ui.ios.IosMenuItem
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import kotlinx.coroutines.flow.MutableStateFlow

private val PaneMenuButtonSize = 48.dp
private val PaneMenuCapsuleSize = 30.dp
private val PaneMenuIconSize = 18.dp
private const val MENU_ALPHA = 0.85f

/** One entry of the pane menu. */
private class PaneAction(
    val label: Int,
    val glyph: IosGlyph?,
    val destructive: Boolean = false,
    val run: () -> Unit
)

/**
 * The "⋯" button of the pane that has the keyboard: split, zoom, swap, close, the broadcast, and the
 * profiles and layouts. It is only on screen when the tab is split, in the header strip of the pane
 * that no text uses. The touch target is 48 dp and the capsule inside it is smaller.
 */
@Composable
internal fun PaneMenu(
    viewModel: TerminalViewModel,
    scene: PaneScene,
    focused: SessionId?,
    screens: ScreenLinks,
    modifier: Modifier
) {
    val isSplit by viewModel.panes.isSplit.collectAsStateWithLifecycle()
    val isZoomed by viewModel.panes.isZoomed.collectAsStateWithLifecycle()
    val broadcast by viewModel.broadcastView.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var choosingGroup by remember { mutableStateOf(false) }
    val chrome = currentChrome()
    val description = stringResource(R.string.pane_menu)
    Box(modifier) {
        Box(
            Modifier
                .size(PaneMenuButtonSize)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        open = true
                        true
                    }
                }
                .pointerInput(Unit) { detectTapGestures { open = true } },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(PaneMenuCapsuleSize)
                    .background(chrome.surface.copy(alpha = MENU_ALPHA), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IosIcon(IosGlyph.ELLIPSIS, null, tint = chrome.onSurface, size = PaneMenuIconSize)
            }
        }
        val close = { open = false }
        val context = PaneMenuContext(viewModel, scene, focused, screens, isSplit, isZoomed)
        val entries = paneActions(context, broadcast) { choosingGroup = true }
        IosContextMenu(expanded = open, onDismiss = close) {
            entries.forEachIndexed { index, entry ->
                IosMenuItem(
                    label = stringResource(entry.label),
                    onClick = {
                        close()
                        entry.run()
                    },
                    glyph = entry.glyph,
                    destructive = entry.destructive,
                    showSeparator = index < entries.lastIndex
                )
            }
        }
    }
    if (choosingGroup) GroupChoice(viewModel, broadcast.focusedGroup) { choosingGroup = false }
}

/** What the entries of the pane menu act on. */
private class PaneMenuContext(
    val viewModel: TerminalViewModel,
    val scene: PaneScene,
    val focused: SessionId?,
    val screens: ScreenLinks,
    val isSplit: Boolean,
    val isZoomed: Boolean
)

private fun paneActions(
    context: PaneMenuContext,
    broadcast: BroadcastView,
    onChooseGroup: () -> Unit
): List<PaneAction> = buildList {
    val viewModel = context.viewModel
    add(
        PaneAction(
            R.string.pane_split_right,
            IosGlyph.CHEVRON_RIGHT,
            run = viewModel.panes::splitVertical
        )
    )
    add(
        PaneAction(
            R.string.pane_split_down,
            IosGlyph.CHEVRON_DOWN,
            run = viewModel.panes::splitHorizontal
        )
    )
    if (context.isSplit) {
        val zoom = if (context.isZoomed) R.string.pane_unzoom else R.string.pane_zoom
        add(PaneAction(zoom, null, run = viewModel.panes::toggleZoom))
        for ((direction, label) in SwapLabels) {
            val focused = context.focused
            if (focused != null && context.scene.neighbour(focused, direction) != null) {
                add(PaneAction(label, null) { viewModel.panes.swap(direction) })
            }
        }
        addAll(broadcastActions(viewModel, broadcast, onChooseGroup))
    }
    add(
        PaneAction(
            R.string.pane_profiles,
            IosGlyph.TERMINAL,
            run = context.screens.profiles.openProfiles
        )
    )
    add(
        PaneAction(
            R.string.pane_layouts,
            IosGlyph.FOLDER,
            run = context.screens.profiles.openLayouts
        )
    )
    if (context.isSplit) {
        add(
            PaneAction(
                R.string.pane_save_layout,
                IosGlyph.DOWNLOAD,
                run = context.screens.profiles.saveLayout
            )
        )
        add(
            PaneAction(
                R.string.pane_close,
                IosGlyph.CLOSE,
                destructive = true,
                run = viewModel.panes::closePane
            )
        )
    }
}

/** The entries about typing in several panes at once (SPEC RF-12). */
private fun broadcastActions(
    viewModel: TerminalViewModel,
    broadcast: BroadcastView,
    onChooseGroup: () -> Unit
): List<PaneAction> = buildList {
    if (broadcast.mode == BroadcastMode.Off) {
        add(PaneAction(R.string.pane_broadcast_start, null, run = viewModel.broadcast::toggle))
    } else {
        add(PaneAction(R.string.pane_broadcast_stop, null, run = viewModel.broadcast::stop))
    }
    val group = broadcast.focusedGroup
    if (group != null && broadcast.mode != BroadcastMode.Group(group)) {
        add(
            PaneAction(
                R.string.pane_broadcast_group_send,
                null,
                run = viewModel.broadcast::sendToFocusedGroup
            )
        )
    }
    add(PaneAction(R.string.pane_broadcast_group, null, run = onChooseGroup))
}

/** The groups a pane can be put in: a few fixed names, or none. */
private val GroupLetters = listOf("A", "B", "C")

@Composable
private fun GroupChoice(viewModel: TerminalViewModel, current: String?, onDismiss: () -> Unit) {
    val names = GroupLetters.map { stringResource(R.string.pane_broadcast_group_name, it) }
    val actions = names.map { name ->
        IosAction(
            if (name ==
                current
            ) {
                "$name ✓"
            } else {
                name
            }
        ) { viewModel.broadcast.assignFocused(name) }
    } +
        IosAction(stringResource(R.string.pane_broadcast_no_group)) {
            viewModel.broadcast.assignFocused(null)
        }
    IosActionSheet(
        actions = actions,
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = onDismiss,
        title = stringResource(R.string.pane_broadcast_group_title)
    )
}

private val SwapLabels = listOf(
    FocusDirection.Left to R.string.pane_swap_left,
    FocusDirection.Right to R.string.pane_swap_right,
    FocusDirection.Up to R.string.pane_swap_up,
    FocusDirection.Down to R.string.pane_swap_down
)
