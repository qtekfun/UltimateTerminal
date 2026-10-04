// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorMathBlendTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun noAmountKeepsTheBaseAndAllOfItGivesTheOtherColor() {
        assertEquals(black, ColorMath.blend(black, white, 0f))
        assertEquals(white, ColorMath.blend(black, white, 1f))
    }

    @Test
    fun halfWayIsTheMiddleGreyAndAlwaysOpaque() {
        assertEquals(0xFF7F7F7F.toInt(), ColorMath.blend(black, white, 0.5f))
        assertEquals(0xFF000000.toInt(), ColorMath.blend(0x00000000, 0x00000000, 0.5f))
    }

    @Test
    fun theAmountIsClampedAndEachChannelIsBlendedAlone() {
        assertEquals(white, ColorMath.blend(black, white, 7f))
        assertEquals(black, ColorMath.blend(black, white, -3f))
        assertEquals(
            0xFF00FF00.toInt(),
            ColorMath.blend(0xFF0000FF.toInt(), 0xFF00FF00.toInt(), 1f)
        )
        assertEquals(0xFF00007F.toInt(), ColorMath.blend(black, 0xFF0000FF.toInt(), 0.5f))
    }

    @Test
    fun aColorThatIsReadableIsLeftAlone() {
        assertEquals(white, ColorMath.readableOn(white, listOf(black), 4.5))
    }

    @Test
    fun aFaintColorIsPushedTowardsWhiteOnADarkBackground() {
        val faint = 0xFF333333.toInt()

        val fixed = ColorMath.readableOn(faint, listOf(black), 4.5)

        assertTrue(ColorMath.contrast(fixed, black) >= 4.5)
        assertTrue(ColorMath.luminance(fixed) > ColorMath.luminance(faint))
    }

    @Test
    fun aFaintColorIsPushedTowardsBlackOnALightBackground() {
        val faint = 0xFFCCCCCC.toInt()

        val fixed = ColorMath.readableOn(faint, listOf(white), 4.5)

        assertTrue(ColorMath.contrast(fixed, white) >= 4.5)
        assertTrue(ColorMath.luminance(fixed) < ColorMath.luminance(faint))
    }

    @Test
    fun theColorMustBeReadableOnEveryBackground() {
        val grey = 0xFF808080.toInt()

        val fixed = ColorMath.readableOn(grey, listOf(black, 0xFF202020.toInt()), 4.5)

        assertTrue(ColorMath.contrast(fixed, black) >= 4.5)
        assertTrue(ColorMath.contrast(fixed, 0xFF202020.toInt()) >= 4.5)
    }

    @Test
    fun anImpossibleMinimumEndsAtTheExtreme() {
        assertEquals(white, ColorMath.readableOn(0xFF777777.toInt(), listOf(black), 30.0))
    }
}
