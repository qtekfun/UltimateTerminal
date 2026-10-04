// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import kotlin.math.pow

/** Color arithmetic on ARGB ints, used to judge legibility (WCAG 2.x definitions). */
object ColorMath {
    private const val CHANNEL_MAX = 255.0
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL_MASK = 0xFF
    private const val LINEAR_THRESHOLD = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_SCALE = 1.055
    private const val GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val FLARE = 0.05
    private const val OPAQUE = -0x1000000 // 0xff000000
    private const val BLACK = OPAQUE
    private const val WHITE = -0x1 // 0xffffffff
    private const val READABLE_STEP = 0.1f

    /**
     * Luminance at which white and black text have the same contrast: above it a background counts
     * as light, below it as dark.
     */
    const val DARK_LIGHT_BOUNDARY = 0.179

    /** Relative luminance, 0 (black) to 1 (white). The alpha channel is ignored. */
    fun luminance(argb: Int): Double {
        val red = linear(argb shr RED_SHIFT and CHANNEL_MASK)
        val green = linear(argb shr GREEN_SHIFT and CHANNEL_MASK)
        val blue = linear(argb and CHANNEL_MASK)
        return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
    }

    /** Contrast ratio between two colors, from 1 (identical) to 21 (black on white). */
    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + FLARE) / (minOf(a, b) + FLARE)
    }

    /** [base] moved [amount] (0 to 1) of the way towards [over], channel by channel; opaque. */
    fun blend(base: Int, over: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = base shr shift and CHANNEL_MASK
            val b = over shr shift and CHANNEL_MASK
            return (a + (b - a) * t).toInt().coerceIn(0, CHANNEL_MASK)
        }
        return OPAQUE or (channel(RED_SHIFT) shl RED_SHIFT) or
            (channel(GREEN_SHIFT) shl GREEN_SHIFT) or channel(0)
    }

    /**
     * [color], or the nearest color towards black or white that reaches [minimum] contrast against
     * every one of [backgrounds]. A color that already does is returned as it is; when even the
     * extreme does not (it cannot happen for a minimum of 4.5 or less), that extreme is returned.
     */
    fun readableOn(color: Int, backgrounds: List<Int>, minimum: Double): Int {
        fun ok(candidate: Int) = backgrounds.all { contrast(candidate, it) >= minimum }
        val towards = if (backgrounds.all { luminance(it) < DARK_LIGHT_BOUNDARY }) WHITE else BLACK
        val closer = generateSequence(READABLE_STEP) { it + READABLE_STEP }
            .takeWhile { it < 1f }
            .map { blend(color, towards, it) }
        return if (ok(color)) color else closer.firstOrNull(::ok) ?: towards
    }

    private fun linear(channel: Int): Double {
        val value = channel / CHANNEL_MAX
        return if (value <= LINEAR_THRESHOLD) {
            value / LINEAR_DIVISOR
        } else {
            ((value + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
        }
    }
}
