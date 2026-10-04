// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.ScrollAccumulator
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.qtekfun.ultimateterminal.domain.terminal.clampTopRow
import com.qtekfun.ultimateterminal.domain.terminal.settled
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State of the terminal screen; survives configuration changes, so the shell keeps running. */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private var layout: TerminalLayout? = null
    private val requestedLayouts = MutableSharedFlow<TerminalLayout>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val host = TerminalSessionHost(application)
    private val scroll = ScrollAccumulator()
    private val topRowState = MutableStateFlow(0)
    private val selectionState = MutableStateFlow<TerminalSelection?>(null)

    val frame: StateFlow<Int> = host.frame
    val exitStatus: StateFlow<Int?> = host.exitStatus
    val keyboard: TerminalInputSink = TerminalKeyboard(host, ::scrollToLiveScreen)

    /** 0 shows the live screen; negative values scroll back through the history. */
    val topRow: StateFlow<Int> = topRowState.asStateFlow()
    val selection: StateFlow<TerminalSelection?> = selectionState.asStateFlow()

    val emulator get() = host.emulator

    private val transcriptRows: Int get() = emulator?.screen?.activeTranscriptRows ?: 0

    init {
        viewModelScope.launch {
            requestedLayouts.settled(RESIZE_DEBOUNCE_MILLIS).collect(::applyLayout)
        }
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
        layout = newLayout
        host.resize(newLayout.grid, newLayout.cellWidthPx, newLayout.cellHeightPx)
        topRowState.value = clampTopRow(topRowState.value, transcriptRows)
    }

    /** Starts a new shell, at the current size, after the previous one ended. */
    fun restart() {
        host.stop()
        topRowState.value = 0
        selectionState.value = null
        layout?.let { host.resize(it.grid, it.cellWidthPx, it.cellHeightPx) }
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
        if (text.isNotEmpty()) host.copyToClipboard(text)
        selectionState.value = null
    }

    private fun scrollToLiveScreen() {
        topRowState.value = 0
        scroll.reset()
    }

    override fun onCleared() {
        host.stop()
    }

    private companion object {
        /** Long enough to skip the frames of a keyboard animation, short enough to feel instant. */
        const val RESIZE_DEBOUNCE_MILLIS = 120L
    }
}
