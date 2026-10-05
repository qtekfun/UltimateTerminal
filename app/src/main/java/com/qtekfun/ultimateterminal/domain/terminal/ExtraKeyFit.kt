// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/**
 * How the labels of the extra-keys row behave with a large system font (T17). The row is a fixed
 * 48 dp tall per row and every key is a share of the width, so a label cannot grow without bound:
 * past [MAX_FONT_SCALE] the cap would be cut. The spoken name of each key is not affected (it is a
 * content description), so capping only trades a larger drawn label for one that is never clipped.
 */
object ExtraKeyFit {
    /** The largest system font scale the drawn labels follow. */
    const val MAX_FONT_SCALE = 1.2f

    /** An average advance of an upper-case Latin letter, in em: a conservative width estimate. */
    private const val EM_PER_CHARACTER = 0.65f

    /** The font scale the labels are drawn with when the system asks for [systemScale]. */
    fun labelFontScale(systemScale: Float): Float = systemScale.coerceIn(1f, MAX_FONT_SCALE)

    /** An estimate, in dp, of the width of [symbol] at [labelSp] scaled by [systemScale]. */
    fun labelWidthDp(symbol: String, labelSp: Float, systemScale: Float): Float =
        symbol.codePointCount(0, symbol.length) * EM_PER_CHARACTER * labelSp *
            labelFontScale(systemScale)

    /** Whether [symbol] fits in a key that is [keyWidthDp] wide with [insetDp] of air on each side. */
    fun fits(
        symbol: String,
        keyWidthDp: Float,
        insetDp: Float,
        labelSp: Float,
        systemScale: Float
    ): Boolean = labelWidthDp(symbol, labelSp, systemScale) <= keyWidthDp - 2 * insetDp
}
