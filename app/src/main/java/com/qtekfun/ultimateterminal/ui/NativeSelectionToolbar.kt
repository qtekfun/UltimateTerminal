// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.terminal.VisibleSelection
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Longer than this, Android shows a hidden floating menu again by itself (3 s): renew before. */
private const val RENEW_HIDE_MILLIS = 2_000L
private const val HIDE_MILLIS = 3_000L
private const val SELECT_LINE_ID = 0x5e1ec7

/**
 * Android's own floating text menu (the one text views show) over the terminal's selection. It is
 * placed against the part of the selection that is on screen ([visible], null when none is), it
 * stays out of the way while [hidden] (a finger scrolls or stretches the selection), and the system
 * puts what does not fit into its overflow menu. Closing it from the system (back) calls
 * [SelectionActions.dismiss].
 */
@Composable
internal fun NativeSelectionToolbar(
    visible: VisibleSelection?,
    painter: TerminalPainter,
    origin: () -> Offset,
    hidden: Boolean,
    actions: SelectionActions
) {
    val view = LocalView.current
    val handleSpace = with(LocalDensity.current) { (HandleRadius * 2).toPx() }
    val labels = MenuLabels(
        copy = stringResource(R.string.terminal_copy),
        paste = stringResource(R.string.terminal_paste),
        selectAll = stringResource(R.string.terminal_select_all),
        selectLine = stringResource(R.string.terminal_select_line)
    )
    val currentVisible by rememberUpdatedState(visible)
    val currentActions by rememberUpdatedState(actions)
    val currentPainter by rememberUpdatedState(painter)
    val currentOrigin by rememberUpdatedState(origin)
    val holder = remember { ModeHolder() }
    val shown = visible != null

    DisposableEffect(shown, labels) {
        if (!shown) return@DisposableEffect onDispose { }
        val callback = SelectionCallback(
            labels = labels,
            onAction = { id -> currentActions.run(id) },
            contentRect = { outRect ->
                currentVisible?.let {
                    outRect.cover(it, currentPainter, currentOrigin(), handleSpace)
                }
            },
            onDestroyed = { if (!holder.closing) currentActions.dismiss() }
        )
        holder.mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        onDispose { holder.finish() }
    }
    LaunchedEffect(visible, painter) { holder.mode?.invalidateContentRect() }
    LaunchedEffect(hidden, shown) {
        val mode = holder.mode ?: return@LaunchedEffect
        if (!hidden) {
            mode.hide(0)
            return@LaunchedEffect
        }
        while (true) {
            mode.hide(HIDE_MILLIS)
            delay(RENEW_HIDE_MILLIS)
        }
    }
}

private data class MenuLabels(
    val copy: String,
    val paste: String,
    val selectAll: String,
    val selectLine: String
)

/** The action mode on screen, and whether it is being closed from here rather than by the system. */
private class ModeHolder {
    var mode: ActionMode? = null
    var closing = false

    fun finish() {
        closing = true
        mode?.finish()
        mode = null
        closing = false
    }
}

private fun SelectionActions.run(id: Int): Boolean {
    when (id) {
        android.R.id.copy -> copy()
        android.R.id.paste -> paste()
        android.R.id.selectAll -> selectAll()
        SELECT_LINE_ID -> selectLine()
        else -> return false
    }
    return true
}

/** The selected cells on screen, in the coordinates of the view that hosts the menu. */
private fun Rect.cover(
    visible: VisibleSelection,
    painter: TerminalPainter,
    origin: Offset,
    below: Float
) {
    val cellW = painter.cellWidth
    val cellH = painter.cellHeight.toFloat()
    set(
        (origin.x + visible.firstColumn * cellW).roundToInt(),
        (origin.y + visible.firstRow * cellH).roundToInt(),
        (origin.x + (visible.lastColumn + 1) * cellW).roundToInt(),
        // The handles hang below the last row: the menu must not cover them.
        (origin.y + (visible.lastRow + 1) * cellH + below).roundToInt()
    )
}

private class SelectionCallback(
    private val labels: MenuLabels,
    private val onAction: (Int) -> Boolean,
    private val contentRect: (Rect) -> Unit,
    private val onDestroyed: () -> Unit
) : ActionMode.Callback2() {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val items = listOf(
            android.R.id.copy to labels.copy,
            android.R.id.paste to labels.paste,
            android.R.id.selectAll to labels.selectAll,
            SELECT_LINE_ID to labels.selectLine
        )
        items.forEachIndexed { order, (id, title) ->
            menu.add(Menu.NONE, id, order, title).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
        onAction(item.itemId)

    override fun onDestroyActionMode(mode: ActionMode) = onDestroyed()

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        contentRect(outRect)
    }
}
