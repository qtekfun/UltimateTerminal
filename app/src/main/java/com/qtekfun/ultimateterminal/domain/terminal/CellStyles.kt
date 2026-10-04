// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import com.termux.terminal.TextStyle

/** What to draw for one cell: colors already resolved to ARGB, plus the text effects that matter. */
data class CellAppearance(
    val foreground: Int,
    val background: Int,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val invisible: Boolean
)

object CellStyles {
    private const val TRUECOLOR_MARKER = -0x1000000 // 0xff000000
    private const val BRIGHT_OFFSET = 8
    private const val NORMAL_COLORS = 8
    private const val ALPHA_OPAQUE = -0x1000000
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL_MASK = 0xFF
    private const val DIM_NUMERATOR = 2
    private const val DIM_DENOMINATOR = 3

    /**
     * Resolves an emulator cell style (as returned by `TerminalRow.getStyle`) against [palette]
     * (`TerminalColors.mCurrentColors`).
     *
     * [reverse] swaps foreground and background on top of the cell's own inverse attribute (screen
     * reverse video, block cursor and selection all use it); if both are set they cancel out.
     */
    fun resolve(style: Long, palette: IntArray, reverse: Boolean): CellAppearance {
        val effect = TextStyle.decodeEffect(style)
        val bold = effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD != 0
        val dim = effect and TextStyle.CHARACTER_ATTRIBUTE_DIM != 0
        val inverse = effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0

        var foreground = colorOf(TextStyle.decodeForeColor(style), palette, brighten = bold)
        var background = colorOf(TextStyle.decodeBackColor(style), palette, brighten = false)
        if (reverse != inverse) {
            val swap = foreground
            foreground = background
            background = swap
        }
        if (dim) foreground = dimmed(foreground)

        return CellAppearance(
            foreground = foreground,
            background = background,
            bold = bold,
            italic = effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC != 0,
            underline = effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE != 0,
            strikethrough = effect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH != 0,
            invisible = effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE != 0
        )
    }

    private fun colorOf(encoded: Int, palette: IntArray, brighten: Boolean): Int = when {
        // 24-bit color: the emulator hands it over already as opaque ARGB.
        encoded and TRUECOLOR_MARKER == TRUECOLOR_MARKER -> encoded

        // Bold text uses the bright variant of the 8 basic colors.
        brighten && encoded in 0 until NORMAL_COLORS -> palette[encoded + BRIGHT_OFFSET]

        else -> palette[encoded]
    }

    private fun dimmed(color: Int): Int {
        val red = channel(color, RED_SHIFT) * DIM_NUMERATOR / DIM_DENOMINATOR
        val green = channel(color, GREEN_SHIFT) * DIM_NUMERATOR / DIM_DENOMINATOR
        val blue = channel(color, 0) * DIM_NUMERATOR / DIM_DENOMINATOR
        return ALPHA_OPAQUE or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    private fun channel(color: Int, shift: Int): Int = color shr shift and CHANNEL_MASK
}
