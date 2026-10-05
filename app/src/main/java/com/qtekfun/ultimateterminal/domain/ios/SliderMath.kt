// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

/** The arithmetic of a slider: where a finger is, which value that means, and the screen reader's steps. */
object SliderMath {
    /** How many steps a screen reader's "increase" and "decrease" cross the whole range in. */
    const val ACCESSIBILITY_STEPS = 20

    /** The place of a touch at [x] on a track [width] wide, 0 (start) to 1 (end), kept on the track. */
    fun fractionAt(x: Float, width: Float): Float =
        if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)

    /** The value [fraction] of the way along [range]. */
    fun valueAt(fraction: Float, range: ClosedFloatingPointRange<Float>): Float =
        range.start + fraction.coerceIn(0f, 1f) * (range.endInclusive - range.start)

    /** How far along [range] [value] is, 0 to 1; a value outside the range is pinned to its end. */
    fun fractionOf(value: Float, range: ClosedFloatingPointRange<Float>): Float {
        val span = range.endInclusive - range.start
        return if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
    }

    /** [value] moved by [steps] steps of a screen reader (negative goes down), kept in [range]. */
    fun stepped(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
        val step = (range.endInclusive - range.start) / ACCESSIBILITY_STEPS
        return (value + steps * step).coerceIn(range.start, range.endInclusive)
    }
}
