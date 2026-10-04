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

    // Palettes are written as hex strings, like the published schemes, and parsed once here.
    private const val HEX_RADIX = 16

    private fun c(hex: String) = hex.toInt(HEX_RADIX) or TerminalColorScheme.BLACK

    private fun scheme(
        name: String,
        ansi: List<String>,
        foreground: String,
        background: String,
        selection: String,
        id: String = name.lowercase().replace(' ', '-'),
        cursor: String = foreground
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
        "073642", "DC322F", "859900", "B58900", "268BD2", "D33682", "2AA198", "EEE8D5",
        "586E75", "CB4B16", "859900", "B58900", "268BD2", "6C71C4", "2AA198", "FDF6E3"
    )

    val solarizedDark = scheme(
        name = "Solarized Dark",
        ansi = solarizedAnsiDark,
        foreground = "93A1A1",
        background = "002B36",
        selection = "073642"
    )

    /**
     * The light variant uses base01 for text (the official base00 is below 4.5:1 on base3) and
     * darkens green, yellow and cyan by 1 to 2 % (the official ones are 2.9:1 to 3.0:1 on base3,
     * under the 3:1 the tests ask of every color a program may print). Everything else is official.
     */
    private val solarizedAnsiLight = listOf(
        "073642", "DC322F", "829500", "B38700", "268BD2", "D33682", "299D94", "EEE8D5",
        "93A1A1", "CB4B16", "829500", "B38700", "268BD2", "6C71C4", "299D94", "FDF6E3"
    )

    val solarizedLight = scheme(
        name = "Solarized Light",
        ansi = solarizedAnsiLight,
        foreground = "586E75",
        background = "FDF6E3",
        selection = "EEE8D5"
    )

    /** Dracula by Zeno Rocha and contributors (MIT). */
    val dracula = scheme(
        name = "Dracula",
        ansi = listOf(
            "21222C", "FF5555", "50FA7B", "F1FA8C", "BD93F9", "FF79C6", "8BE9FD", "F8F8F2",
            "6272A4", "FF6E6E", "69FF94", "FFFFA5", "D6ACFF", "FF92DF", "A4FFFF", "FFFFFF"
        ),
        foreground = "F8F8F2",
        background = "282A36",
        selection = "44475A"
    )

    /**
     * Gruvbox (dark) by Pavel Pertsev (MIT). The normal red is lightened from the official
     * `CC241D` (2.7:1 on the background) to `DE271F`, to reach the 3:1 the tests ask of every
     * color a program may print.
     */
    val gruvboxDark = scheme(
        name = "Gruvbox Dark",
        ansi = listOf(
            "282828", "DE271F", "98971A", "D79921", "458588", "B16286", "689D6A", "A89984",
            "928374", "FB4934", "B8BB26", "FABD2F", "83A598", "D3869B", "8EC07C", "EBDBB2"
        ),
        foreground = "EBDBB2",
        background = "282828",
        selection = "504945"
    )

    /** Nord by Arctic Ice Studio / Sven Greb (MIT). */
    val nord = scheme(
        name = "Nord",
        ansi = listOf(
            "3B4252", "BF616A", "A3BE8C", "EBCB8B", "81A1C1", "B48EAD", "88C0D0", "E5E9F0",
            "4C566A", "BF616A", "A3BE8C", "EBCB8B", "81A1C1", "B48EAD", "8FBCBB", "ECEFF4"
        ),
        foreground = "D8DEE9",
        background = "2E3440",
        selection = "434C5E"
    )

    /** Pure black background for OLED screens, with the bright palette of Dracula (MIT). */
    val oled = scheme(
        id = "oled",
        name = "OLED Black",
        ansi = listOf(
            "000000", "FF5555", "50FA7B", "F1FA8C", "7AA2FF", "FF79C6", "8BE9FD", "E6E6E6",
            "6B6B6B", "FF6E6E", "69FF94", "FFFFA5", "9DBAFF", "FF92DF", "A4FFFF", "FFFFFF"
        ),
        foreground = "E6E6E6",
        background = "000000",
        cursor = "FFFFFF",
        selection = "2D2D2D"
    )

    val all: List<TerminalColorScheme> =
        listOf(dracula, solarizedDark, solarizedLight, gruvboxDark, nord, oled)
}
