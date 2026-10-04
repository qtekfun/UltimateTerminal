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

    private fun linear(channel: Int): Double {
        val value = channel / CHANNEL_MAX
        return if (value <= LINEAR_THRESHOLD) {
            value / LINEAR_DIVISOR
        } else {
            ((value + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
        }
    }
}
