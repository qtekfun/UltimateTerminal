// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.termux.terminal.TextStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TerminalColorSchemeTest {
    private val nord = BuiltInSchemes.nord

    @Test
    fun aSchemeNeedsExactlySixteenAnsiColors() {
        assertThrows(IllegalArgumentException::class.java) { nord.copy(ansi = nord.ansi.drop(1)) }
        assertThrows(IllegalArgumentException::class.java) { nord.copy(ansi = nord.ansi + 0) }
    }

    @Test
    fun oledModeBlacksOutADarkBackgroundAndKeepsTheRest() {
        val oled = nord.forOled()
        assertEquals(TerminalColorScheme.BLACK, oled.background)
        assertEquals(nord.copy(background = TerminalColorScheme.BLACK), oled)
    }

    @Test
    fun oledModeLeavesALightSchemeAlone() {
        val light = BuiltInSchemes.solarizedLight
        assertSame(light, light.forOled())
    }

    @Test
    fun writingIntoAPaletteSetsTheAnsiColorsAndTheDefaults() {
        val palette = IntArray(TextStyle.NUM_INDEXED_COLORS) { 0x12345678 }
        nord.writeInto(palette)

        nord.ansi.forEachIndexed { index, color -> assertEquals(color, palette[index]) }
        assertEquals(nord.foreground, palette[TextStyle.COLOR_INDEX_FOREGROUND])
        assertEquals(nord.background, palette[TextStyle.COLOR_INDEX_BACKGROUND])
        assertEquals(nord.cursor, palette[TextStyle.COLOR_INDEX_CURSOR])
        // The color cube and the grey ramp are not part of a scheme.
        assertEquals(0x12345678, palette[16])
        assertEquals(0x12345678, palette[255])
    }

    @Test
    fun aPaletteThatIsTooSmallIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { nord.writeInto(IntArray(16)) }
    }
}
