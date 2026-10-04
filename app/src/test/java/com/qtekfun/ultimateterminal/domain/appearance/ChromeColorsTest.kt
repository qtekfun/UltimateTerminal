// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.ColorMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChromeColorsTest {
    @Test
    fun everySchemeGivesBarsThatAreReadable() {
        (BuiltInSchemes.all).forEach { scheme ->
            val chrome = ChromeColorsFor.scheme(scheme)

            assertTrue(ColorMath.contrast(chrome.onSurface, chrome.surface) >= 4.5, scheme.name)
            assertTrue(ColorMath.contrast(chrome.onSurface, chrome.selected) >= 4.5, scheme.name)
            assertTrue(ColorMath.contrast(chrome.onAccent, chrome.accent) >= 3.0, scheme.name)
        }
    }

    @Test
    fun theBarsLieBetweenTheBackgroundAndTheText() {
        val scheme = BuiltInSchemes.dracula
        val chrome = ChromeColorsFor.scheme(scheme)
        val bg = ColorMath.luminance(scheme.background)
        val fg = ColorMath.luminance(scheme.foreground)

        listOf(chrome.surface, chrome.selected, chrome.outline).forEach {
            val l = ColorMath.luminance(it)
            assertTrue(l >= minOf(bg, fg) && l <= maxOf(bg, fg))
        }
        assertTrue(
            ColorMath.luminance(chrome.selected) != ColorMath.luminance(chrome.surface)
        )
    }

    @Test
    fun theTextOfTheBarIsTheTextOfTheScheme() {
        assertEquals(
            BuiltInSchemes.nord.foreground,
            ChromeColorsFor.scheme(BuiltInSchemes.nord).onSurface
        )
    }

    @Test
    fun theOledSchemeKeepsPureBlackUnderTheBars() {
        val oled = BuiltInSchemes.dracula.forOled()

        assertEquals(0xFF000000.toInt(), oled.background)
        assertTrue(ChromeColorsFor.scheme(oled).surface != oled.background)
    }
}
