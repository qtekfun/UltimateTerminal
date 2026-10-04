// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What happens to the real emulator when the layout from [terminalLayoutFor] changes: the grid
 * follows it and the text already on screen is kept. It does not cover the pty and its
 * `SIGWINCH`, which need a device (see DECISIONS.md, T04).
 */
class EmulatorResizeTest {
    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) = Unit

        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

        override fun onCopyTextToClipboard(text: String?) = Unit

        override fun onPasteTextFromClipboard() = Unit

        override fun onBell() = Unit

        override fun onColorsChanged() = Unit
    }

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) = Unit

        override fun onTitleChanged(changedSession: TerminalSession) = Unit

        override fun onSessionFinished(finishedSession: TerminalSession) = Unit

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit

        override fun onPasteTextFromClipboard(session: TerminalSession) = Unit

        override fun onBell(session: TerminalSession) = Unit

        override fun onColorsChanged(session: TerminalSession) = Unit

        override fun onTerminalCursorStateChange(state: Boolean) = Unit

        override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

        override fun logError(tag: String?, message: String?) = Unit

        override fun logWarn(tag: String?, message: String?) = Unit

        override fun logInfo(tag: String?, message: String?) = Unit

        override fun logDebug(tag: String?, message: String?) = Unit

        override fun logVerbose(tag: String?, message: String?) = Unit

        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit

        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    private val phone = terminalLayoutFor(1080, 2400, EdgeInsets(0, 72, 0, 132), 12.5f, 30)
    private val emulator = TerminalEmulator(
        output,
        phone.grid.columns,
        phone.grid.rows,
        phone.cellWidthPx,
        phone.cellHeightPx,
        10_000,
        client
    )

    private fun feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        emulator.append(bytes, bytes.size)
    }

    private fun rowText(row: Int): String =
        emulator.screen.getSelectedText(0, row, emulator.mColumns, row).trimEnd()

    private fun apply(layout: TerminalLayout) = emulator.resize(
        layout.grid.columns,
        layout.grid.rows,
        layout.cellWidthPx,
        layout.cellHeightPx
    )

    @Test
    fun theEmulatorStartsAtTheGridOfTheLayout() {
        assertEquals(phone.grid.columns, emulator.mColumns)
        assertEquals(phone.grid.rows, emulator.mRows)
    }

    @Test
    fun growingToATabletKeepsTheContentAndTakesTheNewGrid() {
        feed("user@server:~$ ls\r\nlogs  backups")
        val tablet = terminalLayoutFor(2560, 1600, EdgeInsets(0, 0, 0, 48), 12.5f, 30)

        apply(tablet)

        assertEquals(tablet.grid.columns, emulator.mColumns)
        assertEquals(tablet.grid.rows, emulator.mRows)
        assertEquals("user@server:~$ ls", rowText(0))
        assertEquals("logs  backups", rowText(1))
    }

    @Test
    fun theKeyboardAppearingAndDisappearingRestoresTheOriginalGrid() {
        feed("top - 10:00:00 up 3 days")
        val withKeyboard = terminalLayoutFor(
            1080,
            2400,
            EdgeInsets(0, 72, 0, 132) union EdgeInsets(0, 0, 0, 1000),
            12.5f,
            30
        )

        apply(withKeyboard)
        assertEquals(withKeyboard.grid.rows, emulator.mRows)
        assertEquals(phone.grid.columns, emulator.mColumns)

        apply(phone)
        assertEquals(phone.grid, GridSize(emulator.mColumns, emulator.mRows))
        assertEquals("top - 10:00:00 up 3 days", rowText(0))
    }

    @Test
    fun shrinkingTheWindowKeepsTheLinesThatScrolledOffInTheHistory() {
        for (line in 1..80) feed("line $line\r\n")
        val before = emulator.screen.activeTranscriptRows
        val split = terminalLayoutFor(640, 800, EdgeInsets.NONE, 12.5f, 30)

        apply(split)

        assertEquals(split.grid.rows, emulator.mRows)
        assertTrue(emulator.screen.activeTranscriptRows >= before)
        val oldest = emulator.screen.getSelectedText(
            0,
            -emulator.screen.activeTranscriptRows,
            20,
            -emulator.screen.activeTranscriptRows
        )
        assertTrue(oldest.trimEnd().startsWith("line "), "history keeps whole lines: $oldest")
    }

    @Test
    fun resizingToTheSameLayoutChangesNothing() {
        feed("same")
        apply(phone)
        assertEquals(phone.grid, GridSize(emulator.mColumns, emulator.mRows))
        assertEquals("same", rowText(0))
    }
}
