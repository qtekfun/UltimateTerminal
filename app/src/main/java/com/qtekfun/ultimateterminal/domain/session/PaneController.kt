// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.terminal.settled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the keyboard shortcuts and the pane menu can ask of the panes of the active tab. */
interface PaneCommands {
    fun splitHorizontal()

    fun splitVertical()

    /** Closes the pane that has the keyboard, asking first if its shell still runs. */
    fun closePane()

    fun toggleZoom()

    fun focus(direction: FocusDirection)
}

/** The area the panes share, in pixels, and what is needed to turn it into cells. */
data class PaneArea(
    val widthPx: Int,
    val heightPx: Int,
    val cellWidthPx: Float,
    val cellHeightPx: Int,
    val dividerPx: Int
)

/** Why a split did not happen. */
enum class SplitRefusal { TooSmall }

/**
 * The panes of the active tab: where each is drawn ([scene]), what the user can do with them, and
 * the size each pty is told. Every pane is its own session and the shells of the others are never
 * interrupted by a change here. The decisions are pure and tested; this class only connects them
 * to the session owner.
 */
class PaneController(
    private val editor: PaneEditor,
    scope: CoroutineScope,
    debounceMillis: Long = RESIZE_DEBOUNCE_MILLIS
) : PaneCommands {
    private val area = MutableStateFlow<PaneArea?>(null)
    private val refused = MutableSharedFlow<SplitRefusal>(extraBufferCapacity = 1)

    /** Closing a pane whose shell still runs waits here for the user's answer. */
    val closing = PaneCloseConfirmation(editor, scope)

    /** Where the panes of the active tab are drawn; null until the screen reports its area. */
    val scene: StateFlow<PaneScene?> = combine(editor.state, area, ::sceneOf)
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The pane that has the keyboard. */
    val focused: StateFlow<SessionId?> = editor.state.map { it.activeId }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Whether the active tab has more than one pane, shown all together or not. */
    val isSplit: StateFlow<Boolean> = editor.state
        .map { sessions ->
            sessions.activeId?.let { sessions.paneIdsOf(sessions.tabOf(it)).size > 1 } ==
                true
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Whether the active tab shows only its focused pane. */
    val isZoomed: StateFlow<Boolean> = editor.state
        .map { sessions ->
            sessions.activeId?.let { sessions.panes[sessions.tabOf(it)]?.zoomed } ==
                true
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** A split that was asked for and refused. */
    val refusals: SharedFlow<SplitRefusal> = refused.asSharedFlow()

    init {
        scope.launch {
            combine(editor.state, area.filterNotNull(), ::splitLayouts)
                .settled(debounceMillis)
                .collect(editor::applyPaneLayouts)
        }
    }

    /** The area the panes share changed (window, keyboard, system bars or font). */
    fun onArea(newArea: PaneArea) {
        area.value = newArea
    }

    override fun splitHorizontal() = split(SplitOrientation.HORIZONTAL)

    override fun splitVertical() = split(SplitOrientation.VERTICAL)

    private fun split(orientation: SplitOrientation) {
        val current = area.value
        val rect = focused.value?.let { scene.value?.rectOf(it) }
        val fits = current == null || rect == null ||
            canSplit(
                rect,
                orientation,
                current.cellWidthPx,
                current.cellHeightPx,
                current.dividerPx
            )
        if (fits) editor.splitActive(orientation) else refused.tryEmit(SplitRefusal.TooSmall)
    }

    override fun closePane() = closing.request()

    override fun toggleZoom() = editor.edit { zoomToggled() }

    override fun focus(direction: FocusDirection) {
        val from = focused.value ?: return
        scene.value?.neighbour(from, direction)?.let(::focusPane)
    }

    /** Gives the keyboard to [id] (a tap on a pane). */
    fun focusPane(id: SessionId) = editor.edit { focused(id) }

    /** The user is dragging [divider]: [pointerPx] is the pointer along the axis of the split. */
    fun dragDivider(divider: Divider, pointerPx: Float) {
        val current = area.value ?: return
        val minPane = if (divider.orientation == SplitOrientation.VERTICAL) {
            (MIN_PANE_COLUMNS * current.cellWidthPx).toInt()
        } else {
            MIN_PANE_ROWS * current.cellHeightPx
        }
        val ratio = ratioForPointer(divider, pointerPx, current.dividerPx, minPane)
        editor.edit { ratioSet(divider.path, ratio) }
    }

    /** Exchanges the pane that has the keyboard with its neighbour in [direction]. */
    fun swap(direction: FocusDirection) {
        val from = focused.value ?: return
        val other = scene.value?.neighbour(from, direction) ?: return
        editor.edit { swappedPanes(from, other) }
    }

    private companion object {
        const val RESIZE_DEBOUNCE_MILLIS = 120L
    }
}

/** Where the panes of the active tab are for [area]; null without a tab or an area. */
internal fun sceneOf(sessions: Sessions, area: PaneArea?): PaneScene? =
    sessions.activeId?.takeIf { area != null }?.let { active ->
        val bounds = PaneRect(0, 0, area!!.widthPx, area.heightPx)
        paneScene(sessions.visibleTree(sessions.tabOf(active)), bounds, area.dividerPx)
    }

/**
 * The size of every visible pane, but only for a split tab: the lone session of a tab that was
 * never split is sized with the whole area (see [SessionController.onLayout]). A zoomed pane is
 * the only one visible and gets the whole area too.
 */
internal fun splitLayouts(sessions: Sessions, area: PaneArea): Map<SessionId, TerminalLayout> {
    val split = sessions.activeId?.let { sessions.paneIdsOf(sessions.tabOf(it)).size > 1 } == true
    val scene = if (split) sceneOf(sessions, area) else null
    return scene?.let { paneLayouts(it, area.cellWidthPx, area.cellHeightPx) }.orEmpty()
}
