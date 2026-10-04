// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.termux.terminal.TerminalEmulator

/** Sends what the keyboard types to whichever session is active when the key is pressed. */
class ActiveSessionOutput(private val manager: SessionManager) : TerminalOutput {
    override val emulator: TerminalEmulator? get() = manager.currentHost()?.emulator

    override fun write(text: String) {
        manager.currentHost()?.write(text)
    }

    override fun writeCodePoint(escapePrefix: Boolean, codePoint: Int) {
        manager.currentHost()?.writeCodePoint(escapePrefix, codePoint)
    }
}
