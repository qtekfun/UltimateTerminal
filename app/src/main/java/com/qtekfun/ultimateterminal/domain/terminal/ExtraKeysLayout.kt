// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/**
 * The insets with [heightPx] more at the bottom: the extra-keys row sits above the keyboard and
 * the system bars, so the terminal grid must not count the space it takes.
 */
fun EdgeInsets.reserveBottom(heightPx: Int): EdgeInsets = copy(bottom = bottom + heightPx)

/** The height of the extra-keys row: [rows] rows of [rowHeightPx], or nothing when it is hidden. */
fun extraKeysHeightPx(config: ExtraKeysConfig, rowHeightPx: Int): Int =
    if (config.visible) config.resolved().size * rowHeightPx else 0
