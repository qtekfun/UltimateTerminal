// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.terminal.CellPosition
import com.qtekfun.ultimateterminal.domain.terminal.TerminalSelection
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
