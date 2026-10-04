// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Feeds real escape sequences (what `ls --color`, `top` or vim print) to the real emulator and
 * checks the screen as the painter will read it, through [CellStyles].
 */
class EmulatorScreenTest {
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

    private val emulator = TerminalEmulator(output, COLUMNS, ROWS, 10, 20, 100, client)
    private val palette get() = emulator.mColors.mCurrentColors

    private fun feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        emulator.append(bytes, bytes.size)
    }

    private fun rowText(row: Int): String =
        emulator.screen.getSelectedText(0, row, emulator.mColumns, row).trimEnd()

    private fun appearanceAt(column: Int, row: Int = 0) =
        CellStyles.resolve(emulator.screen.getStyleAt(row, column), palette, reverse = false)

    @Test
    fun plainTextLandsOnTheFirstRow() {
        feed("hello world")
        assertEquals("hello world", rowText(0))
        assertEquals(11, emulator.cursorCol)
        assertEquals(0, emulator.cursorRow)
    }

    @Test
    fun carriageReturnAndLineFeedMoveTheCursor() {
        feed("ab\r\ncd")
        assertEquals("ab", rowText(0))
        assertEquals("cd", rowText(1))
        assertEquals(2, emulator.cursorCol)
        assertEquals(1, emulator.cursorRow)
    }

    @Test
    fun basicColorUsesThePaletteEntry() {
        feed("\u001b[31mR\u001b[0mx")
        assertEquals(palette[1], appearanceAt(0).foreground)
        assertEquals(palette[TextStyle.COLOR_INDEX_FOREGROUND], appearanceAt(1).foreground)
    }

    @Test
    fun boldBasicColorUsesTheBrightEntry() {
        feed("\u001b[1;31mB")
        assertTrue(appearanceAt(0).bold)
        assertEquals(palette[9], appearanceAt(0).foreground)
    }

    @Test
    fun lsColorsSequence() {
        feed("\u001b[01;34mdir\u001b[0m file")
        assertEquals("dir file", rowText(0))
        assertEquals(palette[12], appearanceAt(0).foreground)
        assertEquals(palette[TextStyle.COLOR_INDEX_FOREGROUND], appearanceAt(4).foreground)
    }

    @Test
    fun indexed256ColorIsResolvedThroughThePalette() {
        feed("\u001b[38;5;196mA\u001b[48;5;21mB")
        assertEquals(palette[196], appearanceAt(0).foreground)
        assertEquals(palette[21], appearanceAt(1).background)
    }

    @Test
    fun trueColorIsUsedAsIs() {
        feed("\u001b[38;2;10;20;30mA\u001b[48;2;200;100;50mB")
        assertEquals(0xff0a141e.toInt(), appearanceAt(0).foreground)
        assertEquals(0xffc86432.toInt(), appearanceAt(1).background)
    }

    @Test
    fun inverseSwapsForegroundAndBackground() {
        feed("\u001b[7mI")
        val cell = appearanceAt(0)
        assertEquals(palette[TextStyle.COLOR_INDEX_BACKGROUND], cell.foreground)
        assertEquals(palette[TextStyle.COLOR_INDEX_FOREGROUND], cell.background)
    }

    @Test
    fun reverseVideoOfTheScreenCancelsTheCellsOwnInverse() {
        feed("\u001b[7mI")
        val cell = CellStyles.resolve(emulator.screen.getStyleAt(0, 0), palette, reverse = true)
        assertEquals(palette[TextStyle.COLOR_INDEX_FOREGROUND], cell.foreground)
        assertEquals(palette[TextStyle.COLOR_INDEX_BACKGROUND], cell.background)
    }

    @Test
    fun textEffectsAreDecoded() {
        feed("\u001b[3;4;9mE\u001b[0m\u001b[8mH")
        val effects = appearanceAt(0)
        assertTrue(effects.italic)
        assertTrue(effects.underline)
        assertTrue(effects.strikethrough)
        assertFalse(effects.invisible)
        assertTrue(appearanceAt(1).invisible)
    }

    @Test
    fun dimTextIsDarkerThanNormal() {
        feed("\u001b[38;2;90;150;240;2mD")
        assertEquals(0xff3c64a0.toInt(), appearanceAt(0).foreground)
    }

    @Test
    fun linesThatScrollOffEndUpInTheScrollback() {
        for (line in 1..40) feed("line $line\r\n")
        assertTrue(emulator.screen.activeTranscriptRows > 0)
        val oldest = -emulator.screen.activeTranscriptRows
        assertEquals("line 1", rowText(oldest))
        assertEquals("line 40", rowText(ROWS - 2))
    }

    @Test
    fun clearingTheScreenKeepsTheCursorHome() {
        feed("junk\u001b[2J\u001b[H")
        assertEquals("", rowText(0))
        assertEquals(0, emulator.cursorCol)
        assertEquals(0, emulator.cursorRow)
    }

    @Test
    fun cursorPositioningAndErasingLikeTopDoes() {
        feed("\u001b[5;10Hx\u001b[K")
        assertEquals("         x", rowText(4))
        assertEquals(10, emulator.cursorCol)
        assertEquals(4, emulator.cursorRow)
    }

    @Test
    fun alternateScreenIsSeparateFromTheMainOne() {
        feed("main")
        feed("\u001b[?1049h")
        assertTrue(emulator.isAlternateBufferActive)
        assertEquals("", rowText(0))
        feed("alt")
        feed("\u001b[?1049l")
        assertFalse(emulator.isAlternateBufferActive)
        assertEquals("main", rowText(0))
    }

    @Test
    fun wideCharactersTakeTwoColumns() {
        feed("漢a")
        assertEquals(3, emulator.cursorCol)
    }

    @Test
    fun combiningCharactersDoNotAdvanceTheCursor() {
        feed("éx")
        assertEquals(2, emulator.cursorCol)
    }

    @Test
    fun resizingKeepsTheContentAndUpdatesTheGrid() {
        feed("hello")
        emulator.resize(100, 30, 10, 20)
        assertEquals(100, emulator.mColumns)
        assertEquals(30, emulator.mRows)
        assertEquals("hello", rowText(0))
    }

    @Test
    fun applicationCursorKeysModeIsTrackedForTheKeyEncoder() {
        assertFalse(emulator.isCursorKeysApplicationMode)
        feed("\u001b[?1h")
        assertTrue(emulator.isCursorKeysApplicationMode)
        feed("\u001b[?1l")
        assertFalse(emulator.isCursorKeysApplicationMode)
    }

    @Test
    fun hidingTheCursorIsReflected() {
        assertTrue(emulator.shouldCursorBeVisible())
        feed("\u001b[?25l")
        assertFalse(emulator.shouldCursorBeVisible())
    }

    @Test
    fun deviceStatusReportIsAnsweredToTheShell() {
        feed("\u001b[5;10H\u001b[6n")
        assertEquals("\u001b[5;10R", written.toString())
    }

    @Test
    fun selectedTextComesBackFromTheScreen() {
        feed("one two\r\nthree")
        assertEquals("two\nthr", emulator.screen.getSelectedText(4, 0, 2, 1))
    }

    private companion object {
        const val COLUMNS = 80
        const val ROWS = 24
    }
}
