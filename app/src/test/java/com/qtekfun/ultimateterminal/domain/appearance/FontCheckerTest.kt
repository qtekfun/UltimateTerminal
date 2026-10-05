// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    /** Behaves like Android's `Paint.hasGlyph`: a longer string only counts if it is one glyph. */
    private class AndroidLikeProbe(
        private val covered: Set<Int>,
        private val width: (String) -> Float = { 10f }
    ) : FontProbe {
        val asked = mutableListOf<String>()

        override fun loads() = true

        override fun hasGlyphs(text: String): Boolean {
            asked += text
            return text.codePointCount(0, text.length) == 1 && text.codePointAt(0) in covered
        }

        override fun advance(text: String) = width(text)
    }

    private val ascii = (0x20..0x7E).toSet()

    @Test
    fun theGlyphsAreAskedOneCodePointAtATime() {
        val probe = AndroidLikeProbe(ascii)

        assertEquals(FontCheck.Usable, FontChecker.check(probe))
        assertTrue(probe.asked.isNotEmpty())
        assertTrue(probe.asked.all { it.codePointCount(0, it.length) == 1 })
    }

    @Test
    fun aFontMissingOneRequiredGlyphIsStillRejected() {
        val probe = AndroidLikeProbe(ascii - '}'.code)

        assertEquals(FontCheck.MissingGlyphs, FontChecker.check(probe))
    }

    @Test
    fun aNerdFontWithSqueezedWideIconsIsUsable() {
        val nerd = AndroidLikeProbe(ascii + 0xE0B0 + 0xF015)

        assertEquals(FontCheck.Usable, FontChecker.check(nerd))
    }

    @Test
    fun aProportionalFontWithAllTheGlyphsIsNotMonospaced() {
        val probe = AndroidLikeProbe(ascii, width = { text -> if (text == "W") 16f else 10f })

        assertEquals(FontCheck.NotMonospaced, FontChecker.check(probe))
    }
}
