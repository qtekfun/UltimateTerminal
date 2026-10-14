// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
import com.qtekfun.ultimateterminal.domain.terminal.WordBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The text selection of the active terminal, as an observable value. Copying reads the text from the
 * host that is active at that moment, which is why it asks [hostProvider] each time.
 */
class SelectionController(private val hostProvider: () -> TerminalSessionHost?) {
    private val state = MutableStateFlow<TerminalSelection?>(null)

    val selection: StateFlow<TerminalSelection?> = state.asStateFlow()

    fun start(position: CellPosition) {
        state.value = TerminalSelection(position, position)
    }

    fun extend(position: CellPosition) {
        state.value = state.value?.withFocus(position)
    }

    fun clear() {
        state.value = null
    }

    /** Selects the word under [position], or just that cell when it is blank. */
    fun selectWord(position: CellPosition) {
        val line = rowText(position.row)
        val word = line?.let { WordBounds.at(it, position.column) }
        state.value = if (word == null) {
            TerminalSelection(position, position)
        } else {
            TerminalSelection(position.copy(column = word.first), position.copy(column = word.last))
        }
    }

    /** Selects the whole row [row]. */
    fun selectLine(row: Int) {
        val columns = hostProvider()?.emulator?.mColumns ?: return
        state.value = TerminalSelection(CellPosition(0, row), CellPosition(columns - 1, row))
    }

    /** Selects the visible screen and the whole scrollback. */
    fun selectAll() {
        val emulator = hostProvider()?.emulator ?: return
        state.value = TerminalSelection(
            CellPosition(0, -emulator.screen.activeTranscriptRows),
            CellPosition(emulator.mColumns - 1, emulator.mRows - 1)
        )
    }

    /** Moves one end of the selection (a dragged handle); the other end stays where it was. */
    fun moveEnd(movingStart: Boolean, position: CellPosition) {
        val current = state.value ?: return
        state.value = if (movingStart) {
            TerminalSelection(position, current.end)
        } else {
            TerminalSelection(current.start, position)
        }
    }

    private fun rowText(row: Int): String? {
        val emulator = hostProvider()?.emulator
        val inRange = emulator != null && row >= -emulator.screen.activeTranscriptRows &&
            row < emulator.mRows
        return if (emulator != null && inRange) {
            emulator.screen.getSelectedText(0, row, emulator.mColumns - 1, row)?.trimEnd('\n')
        } else {
            null
        }
    }

    /** Copies the selected text to the clipboard and clears the selection. */
    fun copy() {
        val selected = state.value ?: return
        val host = hostProvider()
        val text = host?.emulator?.screen?.getSelectedText(
            selected.start.column,
            selected.start.row,
            selected.end.column,
            selected.end.row
        )
        if (!text.isNullOrEmpty()) host.copyToClipboard(text)
        state.value = null
    }
}
