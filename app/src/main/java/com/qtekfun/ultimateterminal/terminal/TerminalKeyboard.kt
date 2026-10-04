// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKey
import com.qtekfun.ultimateterminal.domain.terminal.InputRouter
import com.qtekfun.ultimateterminal.domain.terminal.KeyEncoder
import com.qtekfun.ultimateterminal.domain.terminal.KeyInput
import com.qtekfun.ultimateterminal.domain.terminal.KeyOutput
import com.qtekfun.ultimateterminal.domain.terminal.RoutedInput

/** Where the keyboard (soft or hardware) sends what the user types. */
interface TerminalInputSink {
    /** Returns true if the key was consumed. */
    fun onKey(input: KeyInput): Boolean

    fun onText(text: String)

    fun onDeleteBefore(count: Int)
}

/**
 * Sends typed keys to the shell of [host], after [router] has decided what each one means.
 * [onInput] runs after every input sent to the shell, so the screen can jump back to the live
 * prompt when the user types while scrolled up. [onShortcut] receives the application shortcuts.
 */
class TerminalKeyboard(
    private val host: TerminalSessionHost,
    private val router: InputRouter,
    private val onInput: () -> Unit,
    private val onShortcut: (AppShortcut) -> Unit
) : TerminalInputSink {
    override fun onKey(input: KeyInput): Boolean = deliver(router.route(input))

    override fun onText(text: String) {
        deliver(router.routeText(text))
    }

    override fun onDeleteBefore(count: Int) {
        host.write(DEL.toString().repeat(count))
        onInput()
    }

    /** A tap on a key of the extra-keys row. */
    fun onExtraKey(key: ExtraKey) {
        deliver(router.press(key))
    }

    private fun deliver(routed: RoutedInput): Boolean = when (routed) {
        is RoutedInput.Shortcut -> {
            onShortcut(routed.shortcut)
            true
        }

        is RoutedInput.Key -> sendKey(routed.input)

        is RoutedInput.Text -> {
            // A terminal expects carriage return for Enter.
            host.write(routed.text.replace('\n', '\r'))
            onInput()
            true
        }

        RoutedInput.Ignored -> false
    }

    private fun sendKey(input: KeyInput): Boolean {
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

    private companion object {
        const val DEL = 0x7f.toChar()
    }
}
