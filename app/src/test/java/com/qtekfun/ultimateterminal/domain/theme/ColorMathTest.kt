// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorMathTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun blackOnWhiteIsTheMaximumContrast() {
        assertEquals(21.0, ColorMath.contrast(black, white), 0.01)
        assertEquals(21.0, ColorMath.contrast(white, black), 0.01)
    }

    @Test
    fun aColorHasNoContrastWithItself() {
        assertEquals(1.0, ColorMath.contrast(0xFF336699.toInt(), 0xFF336699.toInt()), 0.0001)
    }

    @Test
    fun luminanceOfTheExtremesAndOfPureChannels() {
        assertEquals(0.0, ColorMath.luminance(black), 0.0)
        assertEquals(1.0, ColorMath.luminance(white), 0.0001)
        assertEquals(0.2126, ColorMath.luminance(0xFFFF0000.toInt()), 0.0001)
        assertEquals(0.7152, ColorMath.luminance(0xFF00FF00.toInt()), 0.0001)
        assertEquals(0.0722, ColorMath.luminance(0xFF0000FF.toInt()), 0.0001)
    }

    @Test
    fun theDarkLightBoundaryIsWhereWhiteAndBlackTextTie() {
        // At this luminance white and black text have the same contrast.
        val boundary = ColorMath.DARK_LIGHT_BOUNDARY
        assertEquals((1.05) / (boundary + 0.05), (boundary + 0.05) / 0.05, 0.05)
    }

    @Test
    fun theAlphaChannelIsIgnored() {
        assertEquals(ColorMath.luminance(0xFF808080.toInt()), ColorMath.luminance(0x00808080), 0.0)
    }

    @Test
    fun darkChannelsUseTheLinearBranch() {
        // 0x0A is below the sRGB threshold, where luminance is value / 12.92.
        assertTrue(ColorMath.luminance(0xFF0A0A0A.toInt()) < 0.005)
    }
}
