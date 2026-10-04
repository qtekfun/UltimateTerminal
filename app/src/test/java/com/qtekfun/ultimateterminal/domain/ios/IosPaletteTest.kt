// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.ColorMath
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IosPaletteTest {
    private val light = ThemeDecision(dark = false, oled = false)
    private val dark = ThemeDecision(dark = true, oled = false)
    private val oled = ThemeDecision(dark = true, oled = true)
    private val decisions = listOf(light, dark, oled)
    private val schemes: List<TerminalColorScheme?> =
        listOf<TerminalColorScheme?>(null) + BuiltInSchemes.all

    private fun contrast(a: Int, b: Int) = ColorMath.contrast(a, b)

    @Test
    fun theThreeThemesFlagThemselves() {
        assertFalse(iosPalette(light).dark)
        assertTrue(iosPalette(dark).dark)
        assertFalse(iosPalette(dark).oled)
        assertTrue(iosPalette(oled).oled)
    }

    @Test
    fun oledPutsTheWholePageAndNearlyTheCellsOnBlack() {
        val palette = iosPalette(oled)

        assertEquals(0xFF000000.toInt(), palette.background)
        assertEquals(0xFF000000.toInt(), palette.groupedBackground)
        assertTrue(ColorMath.luminance(palette.cell) < 0.01)
        assertTrue(
            ColorMath.luminance(palette.cell) > ColorMath.luminance(palette.groupedBackground)
        )
    }

    @Test
    fun lightHasTheWhiteCellsOnAGreyPageOfIos() {
        val palette = iosPalette(light)

        assertEquals(0xFFFFFFFF.toInt(), palette.cell)
        assertEquals(0xFFF2F2F7.toInt(), palette.groupedBackground)
    }

    @Test
    fun withoutASchemeTheTintIsTheSystemBlueMadeLegible() {
        val palette = iosPalette(dark)

        assertEquals(0xFF0A84FF.toInt(), palette.tint)
        assertTrue(contrast(palette.tint, palette.cell) >= ColorAdjust.TEXT_CONTRAST)
        assertTrue(
            contrast(iosPalette(light).tint, iosPalette(light).cell) >= ColorAdjust.TEXT_CONTRAST
        )
    }

    @Test
    fun theTintFollowsTheBlueOfTheScheme() {
        val first = BuiltInSchemes.all.first()
        val other = BuiltInSchemes.all.first { it.ansi[4] != first.ansi[4] }

        assertNotEquals(iosPalette(dark, first).tint, iosPalette(dark, other).tint)
    }

    @Test
    fun aSchemeBlueThatAlreadyReadsIsUsedAsItIs() {
        val bright = BuiltInSchemes.all.first().copy(
            ansi = List(TerminalColorScheme.ANSI_COLORS) {
                0xFF5599FF.toInt()
            }
        )

        assertEquals(0xFF5599FF.toInt(), iosPalette(dark, bright).tint)
    }

    @Test
    fun everyTextColorReadsOnItsBackgroundInEveryThemeAndScheme() {
        for (decision in decisions) {
            for (scheme in schemes) {
                val palette = iosPalette(decision, scheme)
                val where = "$decision ${scheme?.name}"
                for (surface in listOf(palette.cell, palette.groupedBackground)) {
                    assertTrue(contrast(palette.label, surface) >= 7.0, "label on $where")
                    assertTrue(
                        contrast(palette.secondaryLabel, surface) >= ColorAdjust.TEXT_CONTRAST,
                        "secondary on $where"
                    )
                    assertTrue(
                        contrast(palette.tint, surface) >= ColorAdjust.TEXT_CONTRAST,
                        "tint on $where"
                    )
                    assertTrue(
                        contrast(palette.destructive, surface) >= ColorAdjust.TEXT_CONTRAST,
                        "destructive on $where"
                    )
                }
                assertTrue(
                    contrast(palette.onTint, palette.tint) >= ColorAdjust.TEXT_CONTRAST,
                    "onTint on $where"
                )
            }
        }
    }

    @Test
    fun allColorsAreOpaque() {
        for (decision in decisions) {
            val p = iosPalette(decision, BuiltInSchemes.all.first())
            val colors = listOf(
                p.background, p.groupedBackground, p.cell, p.pressedCell, p.label, p.secondaryLabel,
                p.separator, p.tint, p.onTint, p.destructive, p.switchOn, p.switchOffTrack
            )
            assertTrue(colors.all { it ushr 24 == 0xFF })
        }
    }
}
