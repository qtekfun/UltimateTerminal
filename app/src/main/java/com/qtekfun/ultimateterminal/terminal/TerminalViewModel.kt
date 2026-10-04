// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.UltimateTerminalApp
import com.qtekfun.ultimateterminal.domain.session.SessionState
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.InputRouter
import com.qtekfun.ultimateterminal.domain.terminal.ScrollAccumulator
import com.qtekfun.ultimateterminal.domain.terminal.StickyState
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.qtekfun.ultimateterminal.domain.terminal.clampTopRow
import com.qtekfun.ultimateterminal.domain.terminal.settled
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the terminal screen. The shells do not belong to it: the [SessionManager] owns them, so
 * they keep running when the activity is recreated or closed, and this view model reconnects to the
 * active one (SPEC RF-07).
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val requestedLayouts = MutableSharedFlow<TerminalLayout>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val manager: SessionManager = (application as UltimateTerminalApp).sessionManager
    private val frameState = MutableStateFlow(0)
    private var started = false
    private val scroll = ScrollAccumulator()
    private val topRowState = MutableStateFlow(0)
    private val selectionState = MutableStateFlow<TerminalSelection?>(null)
    private val stickyState = MutableStateFlow(StickyState())
    private val router = InputRouter(onStickyChanged = { stickyState.value = it })
    private val shortcutEvents = MutableSharedFlow<AppShortcut>(extraBufferCapacity = 8)

    // The stored configuration (settings, Room) arrives later; until then the default is used.
    private val extraKeysState = MutableStateFlow(ExtraKeysConfig.default())

    /** Increments whenever the active session's screen changes or another session becomes active. */
    val frame: StateFlow<Int> = frameState.asStateFlow()

    /** The exit status of the active shell once it has ended, null while it runs. */
    val exitStatus: StateFlow<Int?> = manager.state
        .map { (it.active?.state as? SessionState.Exited)?.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val fontSize = FontSizeController()
    private val shortcuts =
        ShortcutHandler(::copySelection, ::pasteFromClipboard, fontSize, shortcutEvents)
    val keyboard = TerminalKeyboard(
        ActiveSessionOutput(manager),
        router,
        ::scrollToLiveScreen,
        shortcuts::handle
    )

    val extraKeys: StateFlow<ExtraKeysConfig> = extraKeysState.asStateFlow()
    val stickyModifiers: StateFlow<StickyState> = stickyState.asStateFlow()

    /** Shortcuts nothing here handles yet (tabs, T09). */
    val appShortcuts: SharedFlow<AppShortcut> = shortcutEvents.asSharedFlow()

    /** 0 shows the live screen; negative values scroll back through the history. */
    val topRow: StateFlow<Int> = topRowState.asStateFlow()
    val selection: StateFlow<TerminalSelection?> = selectionState.asStateFlow()

    val emulator get() = manager.currentHost()?.emulator

    private val transcriptRows: Int get() = emulator?.screen?.activeTranscriptRows ?: 0

    init {
        viewModelScope.launch {
            requestedLayouts.settled(RESIZE_DEBOUNCE_MILLIS).collect(::applyLayout)
        }
        viewModelScope.launch { followActiveSession() }
    }

    // A frame counter that restarts for every host would repeat values when the active session
    // changes, so the screen would not redraw: count every emission instead.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun followActiveSession() {
        manager.activeHost
            .flatMapLatest { it?.frame ?: flowOf(0) }
            .collect { frameState.value++ }
    }

    /**
     * The terminal area changed size. The shell starts on the first call; later changes reach the
     * pty only once they settle, so a keyboard animation or a window drag does not flood it with
     * resizes.
     */
    fun onLayoutChanged(newLayout: TerminalLayout) {
        requestedLayouts.tryEmit(newLayout)
    }

    private fun applyLayout(newLayout: TerminalLayout) {
        manager.onLayout(newLayout)
        // The first layout starts the shell, but only if the app has none yet: after "Exit" there
        // are no sessions and the screen is closing, so nothing may start a new one.
        if (!started) {
            started = true
            if (manager.state.value.items.isEmpty()) manager.newSession()
        }
        topRowState.value = clampTopRow(topRowState.value, transcriptRows)
    }

    /** Starts a new shell, at the current size, after the previous one ended. */
    fun restart() {
        topRowState.value = 0
        selectionState.value = null
        manager.restartActive()
    }

    private fun pasteFromClipboard() {
        manager.currentHost()?.pasteFromClipboard()
    }

    fun scrollBy(deltaPx: Float, lineHeightPx: Float) {
        val lines = scroll.consume(deltaPx, lineHeightPx)
        if (lines != 0) topRowState.value = clampTopRow(topRowState.value - lines, transcriptRows)
    }

    fun startSelection(position: CellPosition) {
        selectionState.value = TerminalSelection(position, position)
    }

    fun extendSelection(position: CellPosition) {
        selectionState.value = selectionState.value?.withFocus(position)
    }

    fun clearSelection() {
        selectionState.value = null
    }

    fun copySelection() {
        val selected = selectionState.value ?: return
        val screen = emulator?.screen ?: return
        val text = screen.getSelectedText(
            selected.start.column,
            selected.start.row,
            selected.end.column,
            selected.end.row
        )
        if (text.isNotEmpty()) manager.currentHost()?.copyToClipboard(text)
        selectionState.value = null
    }

    private fun scrollToLiveScreen() {
        topRowState.value = 0
        scroll.reset()
    }

    private companion object {
        /** Long enough to skip the frames of a keyboard animation, short enough to feel instant. */
        const val RESIZE_DEBOUNCE_MILLIS = 120L
    }
}
