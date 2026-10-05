// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.broadcast.BroadcastController
import com.qtekfun.ultimateterminal.domain.broadcast.InputKind
import com.termux.terminal.TerminalEmulator

/**
 * Sends what the keyboard types to the session that is active when the key is pressed and, while
 * the tab broadcasts (SPEC RF-12), to the other panes the [broadcast] says. The keys are always
 * encoded for the active pane's emulator (cursor and keypad modes): a pane in another mode gets the
 * same bytes, which is what typing into several servers at once means.
 */
class ActiveSessionOutput(
    private val manager: SessionManager,
    private val broadcast: BroadcastController
) : TerminalOutput {
    override val emulator: TerminalEmulator? get() = manager.currentHost()?.emulator

    override fun write(text: String) {
        manager.currentHost()?.write(text)
    }

    override fun writeCodePoint(escapePrefix: Boolean, codePoint: Int) {
        manager.currentHost()?.writeCodePoint(escapePrefix, codePoint)
    }

    override fun write(text: String, kind: InputKind) {
        broadcast.targets(kind).forEach { manager.hostOf(it)?.write(text) }
    }

    override fun writeCodePoint(escapePrefix: Boolean, codePoint: Int, kind: InputKind) {
        broadcast.targets(kind).forEach {
            manager.hostOf(it)?.writeCodePoint(escapePrefix, codePoint)
        }
    }

    /** Pastes the clipboard into the active pane, and into the others of a broadcast. */
    fun pasteFromClipboard() {
        broadcast.targets(InputKind.TEXT).forEach { manager.hostOf(it)?.pasteFromClipboard() }
    }
}
