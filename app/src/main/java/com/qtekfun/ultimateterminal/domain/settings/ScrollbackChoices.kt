// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import kotlin.math.abs

/**
 * The scrollback sizes the settings offer. The emulator library holds every line in memory, so the
 * top is its own limit (50 000), well under what a profile may ask for.
 */
object ScrollbackChoices {
    const val MIN_LINES = 100
    const val MAX_LINES = 50_000
    val options = listOf(1_000, 2_000, 5_000, 10_000, 20_000, MAX_LINES)

    /** The option closest to [lines], for a stored value that is not one of them. */
    fun nearest(lines: Int): Int = options.minBy { abs(it - lines) }

    /** What the emulator is given: the stored value held inside what it can take. */
    fun forEmulator(lines: Int): Int = lines.coerceIn(MIN_LINES, MAX_LINES)
}
