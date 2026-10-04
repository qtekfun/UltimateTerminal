// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pasting goes through the real emulator, which owns bracketed paste mode. */
class PasteTest {
    private val written = StringBuilder()

    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            written.append(String(data, offset, count, Charsets.UTF_8))
        }

        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

        override fun onCopyTextToClipboard(text: String?) = Unit

        override fun onPasteTextFromClipboard() = Unit

        override fun onBell() = Unit

        override fun onColorsChanged() = Unit
    }

    private val client = object : TerminalSessionClient by NoOpSessionClient {}

    private val emulator = TerminalEmulator(output, 20, 5, 10, 20, 100, client)

    private fun feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        emulator.append(bytes, bytes.size)
    }

    @Test
    fun withoutBracketedModeTextIsSentAsIs() {
        emulator.paste("echo hi")
        assertEquals("echo hi", written.toString())
    }

    @Test
    fun withBracketedModeTheTextIsWrapped() {
        feed("\u001b[?2004h")
        emulator.paste("echo hi")
        assertEquals("\u001b[200~echo hi\u001b[201~", written.toString())
    }

    @Test
    fun turningTheModeOffStopsTheWrapping() {
        feed("\u001b[?2004h")
        feed("\u001b[?2004l")
        emulator.paste("x")
        assertEquals("x", written.toString())
    }

    @Test
    fun newlinesBecomeCarriageReturns() {
        emulator.paste("a\nb\r\nc")
        assertEquals("a\rb\rc", written.toString())
    }

    @Test
    fun pastedEscapeCharactersCannotCloseTheBracket() {
        feed("\u001b[?2004h")
        emulator.paste("safe\u001b[201~; rm -rf x")
        assertEquals("\u001b[200~safe[201~; rm -rf x\u001b[201~", written.toString())
    }
}
