// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TerminalAppearanceTest {
    @Test
    fun theDefaultsAreInsideTheirRanges() {
        val defaults = TerminalAppearance()

        assertEquals(defaults, defaults.sanitized())
        assertEquals(FontCatalog.BUNDLED_ID, defaults.fontId)
    }

    @Test
    fun everyNumberIsClampedIntoItsRange() {
        val wild = TerminalAppearance(
            lineSpacing = 9f,
            letterSpacing = -4f,
            marginDp = 999,
            cornerRadiusDp = -3
        ).sanitized()

        assertEquals(TerminalAppearance.LINE_SPACING_RANGE.endInclusive, wild.lineSpacing)
        assertEquals(TerminalAppearance.LETTER_SPACING_RANGE.start, wild.letterSpacing)
        assertEquals(TerminalAppearance.MARGIN_RANGE.last, wild.marginDp)
        assertEquals(TerminalAppearance.CORNER_RANGE.first, wild.cornerRadiusDp)
    }

    @Test
    fun aNumberThatIsNotFiniteFallsBackToItsDefault() {
        val broken = TerminalAppearance(
            lineSpacing = Float.NaN,
            letterSpacing = Float.POSITIVE_INFINITY
        ).sanitized()

        assertEquals(TerminalAppearance.DEFAULT_LINE_SPACING, broken.lineSpacing)
        assertEquals(TerminalAppearance.DEFAULT_LETTER_SPACING, broken.letterSpacing)
    }

    @Test
    fun theEnumsAreKeptWhenSanitizing() {
        val custom = TerminalAppearance(
            cursorShape = CursorShape.BAR,
            cursorBlink = true,
            chromeStyle = ChromeStyle.SYSTEM
        )

        assertEquals(custom, custom.sanitized())
        assertSame(CursorShape.BAR, custom.sanitized().cursorShape)
    }
}
