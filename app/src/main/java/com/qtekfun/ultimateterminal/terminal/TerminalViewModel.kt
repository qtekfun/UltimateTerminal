// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.launch.LaunchMessage
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.session.PaneController
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.SessionState
import com.qtekfun.ultimateterminal.domain.session.TabsController
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.InputRouter
import com.qtekfun.ultimateterminal.domain.terminal.ScrollAccumulator
import com.qtekfun.ultimateterminal.domain.terminal.StickyState
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.terminal.clampTopRow
import com.qtekfun.ultimateterminal.domain.terminal.settled
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val manager: SessionManager,
    distros: DistroRepository
) : ViewModel() {
    private val requestedLayouts = MutableSharedFlow<TerminalLayout>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val frameState = MutableStateFlow(0)
    private var started = false
    private val scroll = ScrollAccumulator()
    private val topRowState = MutableStateFlow(0)
    private val stickyState = MutableStateFlow(StickyState())
    private val router = InputRouter(onStickyChanged = { stickyState.value = it })

    // The stored configuration (settings, Room) arrives later; until then the default is used.
    private val extraKeysState = MutableStateFlow(ExtraKeysConfig.default())

    /** Increments whenever the active session's screen changes or another session becomes active. */
    val frame: StateFlow<Int> = frameState.asStateFlow()

    /** The exit status of the active shell once it has ended, null while it runs. */
    val exitStatus: StateFlow<Int?> = manager.state
        .map { (it.active?.state as? SessionState.Exited)?.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** What the active tab could not start, or started with a caveat; null when all went well. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val launchMessage: StateFlow<LaunchMessage?> = manager.activeHost
        .flatMapLatest { host ->
            host?.let {
                combine(it.problem, it.notice) { problem, notice ->
                    problem?.let(LaunchMessage::Problem) ?: notice?.let(LaunchMessage::Notice)
                }
            } ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val fontSize = FontSizeController()
    val selection = SelectionController(manager::currentHost)

    /** The tab bar: one tab per session, so switching never interrupts the other shells. */
    val tabs = TabsController(manager.editor, distros.observeAll(), viewModelScope)

    /** The shell of any pane, which a pane that does not have the keyboard still draws. */
    val hostOf: (SessionId) -> TerminalSessionHost? = manager::hostOf

    /** The panes of the active tab: split, close, focus and size (T10). */
    val panes = PaneController(manager.editor, viewModelScope)

    private val shortcuts =
        ShortcutHandler(selection::copy, ::pasteFromClipboard, fontSize, tabs, panes)
    val keyboard = TerminalKeyboard(
        ActiveSessionOutput(manager),
        router,
        ::scrollToLiveScreen,
        shortcuts::handle
    )

    val extraKeys: StateFlow<ExtraKeysConfig> = extraKeysState.asStateFlow()
    val stickyModifiers: StateFlow<StickyState> = stickyState.asStateFlow()

    /** 0 shows the live screen; negative values scroll back through the history. */
    val topRow: StateFlow<Int> = topRowState.asStateFlow()

    val emulator get() = manager.currentHost()?.emulator

    private val transcriptRows: Int get() = emulator?.screen?.activeTranscriptRows ?: 0

    init {
        viewModelScope.launch {
            requestedLayouts.settled(RESIZE_DEBOUNCE_MILLIS).collect(::applyLayout)
        }
        viewModelScope.launch { followActiveSession() }
        // The scroll position and the selection belong to one screen: a tab starts at the bottom.
        viewModelScope.launch {
            manager.activeHost.collect {
                scrollToLiveScreen()
                selection.clear()
            }
        }
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
            if (manager.state.value.items.isEmpty()) manager.newDefaultSession()
        }
        topRowState.value = clampTopRow(topRowState.value, transcriptRows)
    }

    /** The colors changed (a new scheme, or OLED mode): every screen is drawn again with them. */
    fun applyScheme(scheme: TerminalColorScheme) {
        manager.applyScheme(scheme)
        frameState.value++
    }

    /** Starts a new shell, at the current size, after the previous one ended. */
    fun restart() {
        topRowState.value = 0
        selection.clear()
        manager.restartActive()
    }

    private fun pasteFromClipboard() {
        manager.currentHost()?.pasteFromClipboard()
    }

    fun scrollBy(deltaPx: Float, lineHeightPx: Float) {
        val lines = scroll.consume(deltaPx, lineHeightPx)
        if (lines != 0) topRowState.value = clampTopRow(topRowState.value - lines, transcriptRows)
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
