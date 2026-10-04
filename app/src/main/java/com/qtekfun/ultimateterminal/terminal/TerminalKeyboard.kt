// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.terminal.KeyEncoder
import com.qtekfun.ultimateterminal.domain.terminal.KeyInput
import com.qtekfun.ultimateterminal.domain.terminal.KeyOutput

/** Where the keyboard (soft or hardware) sends what the user types. */
interface TerminalInputSink {
    /** Returns true if the key was consumed. */
    fun onKey(input: KeyInput): Boolean

    fun onText(text: String)

    fun onDeleteBefore(count: Int)
}

/**
 * Sends typed keys to the shell of [host]. [onInput] runs after every input, so the screen can
 * jump back to the live prompt when the user types while scrolled up.
 */
class TerminalKeyboard(private val host: TerminalSessionHost, private val onInput: () -> Unit) :
    TerminalInputSink {
    override fun onKey(input: KeyInput): Boolean {
        val terminal = host.emulator
        val output = if (terminal == null) {
            KeyOutput.None
        } else {
            KeyEncoder.encode(
                input,
                terminal.isCursorKeysApplicationMode,
                terminal.isKeypadApplicationMode
            )
        }
        when (output) {
            is KeyOutput.Sequence -> host.write(output.text)
            is KeyOutput.CodePoint -> host.writeCodePoint(output.escapePrefix, output.value)
            KeyOutput.None -> Unit
        }
        val sent = output != KeyOutput.None
        if (sent) onInput()
        return sent
    }

    override fun onText(text: String) {
        // A terminal expects carriage return for Enter.
        host.write(text.replace('\n', '\r'))
        onInput()
    }

    override fun onDeleteBefore(count: Int) {
        host.write(DEL.toString().repeat(count))
        onInput()
    }

    private companion object {
        const val DEL = 0x7f.toChar()
    }
}
