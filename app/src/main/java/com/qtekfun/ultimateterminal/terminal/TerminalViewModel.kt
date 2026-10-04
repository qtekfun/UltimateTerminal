// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.domain.terminal.ScrollAccumulator
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.qtekfun.ultimateterminal.domain.terminal.clampTopRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** State of the terminal screen; survives configuration changes, so the shell keeps running. */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private class Layout(val grid: GridSize, val cellWidthPx: Int, val cellHeightPx: Int)

    private var layout: Layout? = null
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

    /** The terminal area changed size: start the shell on first call, resize it afterwards. */
    fun onGridChanged(grid: GridSize, cellWidthPx: Int, cellHeightPx: Int) {
        layout = Layout(grid, cellWidthPx, cellHeightPx)
        host.resize(grid, cellWidthPx, cellHeightPx)
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
}
