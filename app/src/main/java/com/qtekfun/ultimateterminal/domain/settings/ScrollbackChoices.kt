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
    private const val LINES_1K = 1_000
    private const val LINES_2K = 2_000
    private const val LINES_5K = 5_000
    private const val LINES_10K = 10_000
    private const val LINES_20K = 20_000
    val options = listOf(LINES_1K, LINES_2K, LINES_5K, LINES_10K, LINES_20K, MAX_LINES)

    /** The option closest to [lines], for a stored value that is not one of them. */
    fun nearest(lines: Int): Int = options.minBy { abs(it - lines) }

    /** What the emulator is given: the stored value held inside what it can take. */
    fun forEmulator(lines: Int): Int = lines.coerceIn(MIN_LINES, MAX_LINES)
}
