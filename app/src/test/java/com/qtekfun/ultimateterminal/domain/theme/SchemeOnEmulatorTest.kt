// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.qtekfun.ultimateterminal.domain.terminal.CellStyles
import com.qtekfun.ultimateterminal.domain.terminal.NoOpSessionClient
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TextStyle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A scheme applied the way the session host does it, checked on the real emulator. */
class SchemeOnEmulatorTest {
    private val shared = TerminalColors.COLOR_SCHEME.mDefaultColors
    private val original = shared.copyOf()

    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) = Unit

        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

        override fun onCopyTextToClipboard(text: String?) = Unit

        override fun onPasteTextFromClipboard() = Unit

        override fun onBell() = Unit

        override fun onColorsChanged() = Unit
    }

    private fun emulator() = TerminalEmulator(output, 20, 5, 10, 20, 100, NoOpSessionClient)

    // The default scheme is shared by the whole library: leave it as the other tests expect it.
    @AfterEach
    fun restore() {
        original.copyInto(shared)
    }

    @Test
    fun aNewShellStartsWithTheSchemeColors() {
        BuiltInSchemes.dracula.writeInto(shared)
        val palette = emulator().mColors.mCurrentColors

        assertEquals(BuiltInSchemes.dracula.ansi[1], palette[1])
        assertEquals(BuiltInSchemes.dracula.foreground, palette[TextStyle.COLOR_INDEX_FOREGROUND])
        assertEquals(BuiltInSchemes.dracula.background, palette[TextStyle.COLOR_INDEX_BACKGROUND])
        assertEquals(BuiltInSchemes.dracula.cursor, palette[TextStyle.COLOR_INDEX_CURSOR])
    }

    @Test
    fun aRunningShellTakesANewSchemeAndDropsColorsAProgramSet() {
        val running = emulator()
        running.mColors.mCurrentColors[1] = 0xFF123456.toInt() // as OSC 4 would
        BuiltInSchemes.nord.writeInto(shared)
        running.mColors.reset()

        assertEquals(BuiltInSchemes.nord.ansi[1], running.mColors.mCurrentColors[1])
        assertEquals(
            BuiltInSchemes.nord.background,
            running.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
        )
    }

    @Test
    fun cellsAreResolvedAgainstTheSchemeSoTextIsDrawnInItsColors() {
        BuiltInSchemes.solarizedLight.writeInto(shared)
        val running = emulator()
        val bytes = "\u001b[31mR\u001b[0mx".toByteArray()
        running.append(bytes, bytes.size)

        val red = CellStyles.resolve(
            running.screen.getStyleAt(0, 0),
            running.mColors.mCurrentColors,
            false
        )
        val plain = CellStyles.resolve(
            running.screen.getStyleAt(0, 1),
            running.mColors.mCurrentColors,
            false
        )
        assertEquals(BuiltInSchemes.solarizedLight.ansi[1], red.foreground)
        assertEquals(BuiltInSchemes.solarizedLight.foreground, plain.foreground)
        assertEquals(BuiltInSchemes.solarizedLight.background, plain.background)
    }

    @Test
    fun anOledSchemeGivesABlackBackground() {
        BuiltInSchemes.nord.forOled().writeInto(shared)
        assertEquals(
            TerminalColorScheme.BLACK,
            emulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]
        )
    }
}
