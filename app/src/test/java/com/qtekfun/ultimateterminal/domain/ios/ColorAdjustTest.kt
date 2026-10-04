// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import com.qtekfun.ultimateterminal.domain.theme.ColorMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorAdjustTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun aColorThatIsAlreadyLegibleIsLeftAlone() {
        assertEquals(black, ColorAdjust.ensureContrast(black, white))
        assertEquals(white, ColorAdjust.ensureContrast(white, black))
    }

    @Test
    fun aPaleColorOnWhiteIsDarkenedUntilItReads() {
        val yellow = 0xFFFFE066.toInt()

        val adjusted = ColorAdjust.ensureContrast(yellow, white)

        assertTrue(ColorMath.contrast(adjusted, white) >= ColorAdjust.TEXT_CONTRAST)
        assertTrue(ColorMath.luminance(adjusted) < ColorMath.luminance(yellow))
    }

    @Test
    fun aDarkColorOnBlackIsLightenedUntilItReads() {
        val navy = 0xFF101040.toInt()

        val adjusted = ColorAdjust.ensureContrast(navy, black)

        assertTrue(ColorMath.contrast(adjusted, black) >= ColorAdjust.TEXT_CONTRAST)
        assertTrue(ColorMath.luminance(adjusted) > ColorMath.luminance(navy))
    }

    @Test
    fun theNudgeKeepsTheOpaqueAlphaAndStopsAtTheExtremeIfItMust() {
        val grey = 0xFF777777.toInt()

        assertEquals(black, ColorAdjust.ensureContrast(grey, white, minRatio = 21.0))
        assertEquals(0xFF, ColorAdjust.ensureContrast(0xFFFFE066.toInt(), white) ushr 24)
    }

    @Test
    fun textOnAFilledControlIsWhiteOrBlackWhicheverReadsBetter() {
        assertEquals(white, ColorAdjust.readableOn(0xFF0040DD.toInt()))
        assertEquals(black, ColorAdjust.readableOn(0xFFFFE066.toInt()))
    }
}
