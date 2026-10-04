// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import com.qtekfun.ultimateterminal.domain.theme.ColorMath

/** Nudges colors until text in them is legible, keeping their hue as long as it can. */
object ColorAdjust {
    private const val WHITE = -0x1 // 0xffffffff
    private const val BLACK = -0x1000000 // 0xff000000
    private const val STEPS = 40
    private const val CHANNEL_MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** Contrast WCAG asks of normal text (AA). */
    const val TEXT_CONTRAST = 4.5

    /**
     * [color] moved towards white (on a dark [background]) or black (on a light one) until it has at
     * least [minRatio] contrast against [background]. Unchanged if it already has it; the end of the
     * walk, pure white or black, is what a color with no hue left to keep becomes.
     */
    fun ensureContrast(color: Int, background: Int, minRatio: Double = TEXT_CONTRAST): Int {
        if (ColorMath.contrast(color, background) >= minRatio) return color
        val target = if (ColorMath.luminance(background) <
            ColorMath.DARK_LIGHT_BOUNDARY
        ) {
            WHITE
        } else {
            BLACK
        }
        return (1..STEPS)
            .map { mix(color, target, it.toFloat() / STEPS) }
            .firstOrNull { ColorMath.contrast(it, background) >= minRatio }
            ?: target
    }

    /** Whichever of white and black reads better on [background]. */
    fun readableOn(background: Int): Int = if (ColorMath.contrast(WHITE, background) >=
        ColorMath.contrast(BLACK, background)
    ) {
        WHITE
    } else {
        BLACK
    }

    private fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = from shr shift and CHANNEL_MASK
            val b = to shr shift and CHANNEL_MASK
            return (a + (b - a) * amount).toInt().coerceIn(0, CHANNEL_MASK)
        }
        return BLACK or (
            channel(
                RED_SHIFT
            ) shl RED_SHIFT
            ) or (channel(GREEN_SHIFT) shl GREEN_SHIFT) or
            channel(0)
    }
}
