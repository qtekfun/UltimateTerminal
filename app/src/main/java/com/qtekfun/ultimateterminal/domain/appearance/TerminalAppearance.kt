// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

/** The shape of the terminal cursor. */
enum class CursorShape { BLOCK, UNDERLINE, BAR }

/** Where the colors of the tab bar, the extra keys and the pane outline come from. */
enum class ChromeStyle {
    /** Derived from the terminal color scheme, so the window looks like one piece. */
    SCHEME,

    /** The system theme (Material You colors when they are on). */
    SYSTEM
}

/**
 * How the terminal looks beyond its colors: the font, the spacing, the margin and the cursor, and
 * the style of the bars around it. Every value has a range ([Limits]) and [sanitized] clamps a
 * stored or imported value into it, so a damaged setting never breaks the drawing.
 */
data class TerminalAppearance(
    /** The bundled font ([FontCatalog.BUNDLED_ID]) or the id of an imported one. */
    val fontId: String = FontCatalog.BUNDLED_ID,
    /** Multiplier of the line height; 1 is the font's own. */
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    /** Extra space between letters, in em; it widens every cell of the grid. */
    val letterSpacing: Float = DEFAULT_LETTER_SPACING,
    /** Air between the text and the edges of the terminal, in dp. */
    val marginDp: Int = DEFAULT_MARGIN_DP,
    val cursorShape: CursorShape = CursorShape.BLOCK,
    val cursorBlink: Boolean = false,
    val chromeStyle: ChromeStyle = ChromeStyle.SCHEME,
    /** Corner radius of the tabs, the keys and the buttons of the bars, in dp. */
    val cornerRadiusDp: Int = DEFAULT_CORNER_DP,
    /** How the extra-keys row is drawn. */
    val extraKeyStyle: ExtraKeyStyle = ExtraKeyStyle.DEFAULT
) {
    /** The same appearance with every number inside its range. */
    fun sanitized(): TerminalAppearance = copy(
        lineSpacing = lineSpacing.finiteOr(DEFAULT_LINE_SPACING).coerceIn(LINE_SPACING_RANGE),
        letterSpacing = letterSpacing.finiteOr(DEFAULT_LETTER_SPACING)
            .coerceIn(LETTER_SPACING_RANGE),
        marginDp = marginDp.coerceIn(MARGIN_RANGE),
        cornerRadiusDp = cornerRadiusDp.coerceIn(CORNER_RANGE)
    )

    private fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback

    /** The ranges the UI sliders use and the storage enforces. */
    companion object Limits {
        const val DEFAULT_LINE_SPACING = 1f
        const val DEFAULT_LETTER_SPACING = 0f
        const val DEFAULT_MARGIN_DP = 6
        const val DEFAULT_CORNER_DP = 8
        val LINE_SPACING_RANGE = 0.8f..1.6f
        val LETTER_SPACING_RANGE = -0.05f..0.3f
        val MARGIN_RANGE = 0..24
        val CORNER_RANGE = 0..16
    }
}
