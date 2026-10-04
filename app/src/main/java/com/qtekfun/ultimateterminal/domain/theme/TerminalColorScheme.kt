// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.termux.terminal.TextStyle

/**
 * The colors of the terminal: the 16 ANSI colors, the default foreground and background, the
 * cursor and the selection, all opaque ARGB. The 216-color cube and the grey ramp (indexes 16 to
 * 255) are standard and are not part of a scheme.
 */
data class TerminalColorScheme(
    val id: String,
    val name: String,
    val ansi: List<Int>,
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    val selection: Int,
    val builtIn: Boolean = false
) {
    init {
        require(ansi.size == ANSI_COLORS) { "A scheme has exactly $ANSI_COLORS ANSI colors" }
    }

    /** Whether the background is dark (white text reads better on it than black text). */
    val isDark: Boolean get() = ColorMath.luminance(background) < ColorMath.DARK_LIGHT_BOUNDARY

    /**
     * The scheme as shown in OLED mode: a dark scheme gets a pure black background so the pixels
     * switch off. A light scheme is left as it is; OLED mode only concerns dark themes.
     */
    fun forOled(): TerminalColorScheme = if (isDark) copy(background = BLACK) else this

    /** Writes this scheme into an emulator palette (`TerminalColors.mDefaultColors` layout). */
    fun writeInto(palette: IntArray) {
        require(palette.size >= TextStyle.NUM_INDEXED_COLORS) { "Not an emulator palette" }
        ansi.forEachIndexed { index, color -> palette[index] = color }
        palette[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        palette[TextStyle.COLOR_INDEX_BACKGROUND] = background
        palette[TextStyle.COLOR_INDEX_CURSOR] = cursor
    }

    companion object {
        const val ANSI_COLORS = 16
        const val BLACK = -0x1000000 // 0xff000000
    }
}
