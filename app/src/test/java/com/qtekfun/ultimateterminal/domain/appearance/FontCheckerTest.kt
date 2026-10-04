// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FontCheckerTest {
    /** A fake font: [width] says how wide a text is. */
    private class Probe(
        private val loads: Boolean = true,
        private val glyphs: Boolean = true,
        private val width: (String) -> Float = { 10f }
    ) : FontProbe {
        override fun loads() = loads
        override fun hasGlyphs(text: String) = glyphs
        override fun advance(text: String) = width(text)
    }

    @Test
    fun aMonospacedFontWithTheBasicsIsUsable() {
        assertEquals(FontCheck.Usable, FontChecker.check(Probe()))
    }

    @Test
    fun aFontThatDoesNotLoadIsUnreadable() {
        assertEquals(FontCheck.Unreadable, FontChecker.check(Probe(loads = false)))
    }

    @Test
    fun aFontWithoutTheBasicGlyphsIsRejected() {
        assertEquals(FontCheck.MissingGlyphs, FontChecker.check(Probe(glyphs = false)))
    }

    @Test
    fun aProportionalFontIsRejected() {
        val proportional = Probe(width = { text -> if (text == "i") 4f else 10f })

        assertEquals(FontCheck.NotMonospaced, FontChecker.check(proportional))
    }

    @Test
    fun aTinyDifferenceFromHintingIsTolerated() {
        val hinted = Probe(width = { text -> if (text == "W") 10.05f else 10f })

        assertEquals(FontCheck.Usable, FontChecker.check(hinted))
    }

    @Test
    fun aFontThatMeasuresNothingIsUnreadable() {
        assertEquals(FontCheck.Unreadable, FontChecker.check(Probe(width = { 0f })))
    }
}
