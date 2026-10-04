// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

/**
 * The color schemes that come with the app. The palettes are the published ones of each scheme
 * (credited in THIRD_PARTY_NOTICES.md) with the small changes listed on each, made so that every
 * color a program may print stays readable on the scheme's own background (checked by a test).
 */
object BuiltInSchemes {
    const val DEFAULT_ID = "dracula"

    private fun c(rgb: Int) = rgb or TerminalColorScheme.BLACK

    private fun scheme(
        id: String,
        name: String,
        ansi: List<Int>,
        foreground: Int,
        background: Int,
        cursor: Int,
        selection: Int
    ) = TerminalColorScheme(
        id = id,
        name = name,
        ansi = ansi.map(::c),
        foreground = c(foreground),
        background = c(background),
        cursor = c(cursor),
        selection = c(selection),
        builtIn = true
    )

    /**
     * Solarized by Ethan Schoonover (MIT). The bright colors keep the hue of the normal ones
     * instead of being the greys of the official terminal mapping, so bold text stays colored.
     */
    private val solarizedAnsiDark = listOf(
        0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
        0x586E75, 0xCB4B16, 0x859900, 0xB58900, 0x268BD2, 0x6C71C4, 0x2AA198, 0xFDF6E3
    )

    val solarizedDark = scheme(
        id = "solarized-dark",
        name = "Solarized Dark",
        ansi = solarizedAnsiDark,
        foreground = 0x93A1A1,
        background = 0x002B36,
        cursor = 0x93A1A1,
        selection = 0x073642
    )

    /** The light variant uses base01 for text (the official base00 is below 4.5:1 on base3). */
    val solarizedLight = scheme(
        id = "solarized-light",
        name = "Solarized Light",
        ansi = solarizedAnsiDark.toMutableList().also { it[8] = 0x93A1A1 },
        foreground = 0x586E75,
        background = 0xFDF6E3,
        cursor = 0x586E75,
        selection = 0xEEE8D5
    )

    /** Dracula by Zeno Rocha and contributors (MIT). */
    val dracula = scheme(
        id = "dracula",
        name = "Dracula",
        ansi = listOf(
            0x21222C, 0xFF5555, 0x50FA7B, 0xF1FA8C, 0xBD93F9, 0xFF79C6, 0x8BE9FD, 0xF8F8F2,
            0x6272A4, 0xFF6E6E, 0x69FF94, 0xFFFFA5, 0xD6ACFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF
        ),
        foreground = 0xF8F8F2,
        background = 0x282A36,
        cursor = 0xF8F8F2,
        selection = 0x44475A
    )

    /** Gruvbox (dark) by Pavel Pertsev (MIT). */
    val gruvboxDark = scheme(
        id = "gruvbox-dark",
        name = "Gruvbox Dark",
        ansi = listOf(
            0x282828, 0xCC241D, 0x98971A, 0xD79921, 0x458588, 0xB16286, 0x689D6A, 0xA89984,
            0x928374, 0xFB4934, 0xB8BB26, 0xFABD2F, 0x83A598, 0xD3869B, 0x8EC07C, 0xEBDBB2
        ),
        foreground = 0xEBDBB2,
        background = 0x282828,
        cursor = 0xEBDBB2,
        selection = 0x504945
    )

    /** Nord by Arctic Ice Studio / Sven Greb (MIT). */
    val nord = scheme(
        id = "nord",
        name = "Nord",
        ansi = listOf(
            0x3B4252, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x88C0D0, 0xE5E9F0,
            0x4C566A, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x8FBCBB, 0xECEFF4
        ),
        foreground = 0xD8DEE9,
        background = 0x2E3440,
        cursor = 0xD8DEE9,
        selection = 0x434C5E
    )

    /** Pure black background for OLED screens, with the bright palette of Dracula (MIT). */
    val oled = scheme(
        id = "oled",
        name = "OLED Black",
        ansi = listOf(
            0x000000, 0xFF5555, 0x50FA7B, 0xF1FA8C, 0x7AA2FF, 0xFF79C6, 0x8BE9FD, 0xE6E6E6,
            0x6B6B6B, 0xFF6E6E, 0x69FF94, 0xFFFFA5, 0x9DBAFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF
        ),
        foreground = 0xE6E6E6,
        background = 0x000000,
        cursor = 0xFFFFFF,
        selection = 0x2D2D2D
    )

    val all: List<TerminalColorScheme> =
        listOf(dracula, solarizedDark, solarizedLight, gruvboxDark, nord, oled)
}
