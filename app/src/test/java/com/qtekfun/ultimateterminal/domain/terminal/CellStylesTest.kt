// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import com.termux.terminal.TextStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** Direct tests of the style math, with hand-built styles (the emulator tests cover real input). */
class CellStylesTest {
    private val palette = IntArray(TextStyle.NUM_INDEXED_COLORS) {
        0xff000000.toInt() or
            (it * 0x010101)
    }

    // Bit layout of TextStyle: effects in the low bits, background index at 16, foreground at 40.
    private fun style(foreground: Int, background: Int, effect: Int = 0): Long =
        effect.toLong() or (background.toLong() shl 16) or (foreground.toLong() shl 40)

    @Test
    fun indexedColorsComeFromThePalette() {
        val cell = CellStyles.resolve(
            style(foreground = 3, background = 4),
            palette,
            reverse = false
        )
        assertEquals(palette[3], cell.foreground)
        assertEquals(palette[4], cell.background)
    }

    @Test
    fun boldBrightensOnlyTheEightBasicForegroundColors() {
        val bold = TextStyle.CHARACTER_ATTRIBUTE_BOLD
        assertEquals(palette[10], CellStyles.resolve(style(2, 0, bold), palette, false).foreground)
        assertEquals(palette[8], CellStyles.resolve(style(8, 0, bold), palette, false).foreground)
        assertEquals(
            palette[200],
            CellStyles.resolve(style(200, 0, bold), palette, false).foreground
        )
        assertEquals(palette[2], CellStyles.resolve(style(2, 2, bold), palette, false).background)
    }

    @Test
    fun reverseAndInverseCancelOut() {
        val inverse = TextStyle.CHARACTER_ATTRIBUTE_INVERSE
        val plain = CellStyles.resolve(style(1, 2), palette, reverse = false)
        val swapped = CellStyles.resolve(style(1, 2), palette, reverse = true)
        val inverted = CellStyles.resolve(style(1, 2, inverse), palette, reverse = false)
        val both = CellStyles.resolve(style(1, 2, inverse), palette, reverse = true)
        assertEquals(palette[1], plain.foreground)
        assertEquals(palette[2], swapped.foreground)
        assertEquals(palette[2], inverted.foreground)
        assertEquals(palette[1], both.foreground)
    }

    @Test
    fun noEffectsMeansPlainText() {
        val cell = CellStyles.resolve(style(1, 2), palette, reverse = false)
        assertFalse(
            cell.bold || cell.italic || cell.underline || cell.strikethrough || cell.invisible
        )
    }
}
