// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import kotlin.math.abs

/** What the terminal needs to know about a font file, asked of whoever can load it. */
interface FontProbe {
    /** Whether the font can be loaded at all. */
    fun loads(): Boolean

    /** Whether the font has a glyph for [text] (every code point of it). */
    fun hasGlyphs(text: String): Boolean

    /** The advance width of [text] at some fixed size; only ratios are used. */
    fun advance(text: String): Float
}

/** The verdict on a font file. */
sealed interface FontCheck {
    data object Usable : FontCheck

    /** Not a font, or one the system cannot load. */
    data object Unreadable : FontCheck

    /** The glyphs do not all have the same width, so the cells of the grid would not line up. */
    data object NotMonospaced : FontCheck

    /** The font lacks letters a terminal always shows. */
    data object MissingGlyphs : FontCheck
}

/** Decides whether a font can be a terminal font: it must be monospaced and cover the basics. */
object FontChecker {
    /** Letters of very different natural widths: in a monospaced font they all measure the same. */
    private const val WIDTH_SAMPLES = "iIl1.:|WMm@#0OQ"

    /** What every terminal needs on screen. */
    private const val REQUIRED_GLYPHS = "abcXYZ0189 .,:;/\\|-_[]{}()<>"

    /** Two advances closer than this fraction count as equal (hinting rounds them). */
    private const val TOLERANCE = 0.01f

    fun check(probe: FontProbe): FontCheck = when {
        !probe.loads() -> FontCheck.Unreadable
        !probe.hasGlyphs(REQUIRED_GLYPHS) -> FontCheck.MissingGlyphs
        probe.advance("X") <= 0f -> FontCheck.Unreadable
        isMonospaced(probe) -> FontCheck.Usable
        else -> FontCheck.NotMonospaced
    }

    private fun isMonospaced(probe: FontProbe): Boolean {
        val reference = probe.advance("X")
        return WIDTH_SAMPLES.all { sample ->
            abs(probe.advance(sample.toString()) - reference) <= reference * TOLERANCE
        }
    }
}
