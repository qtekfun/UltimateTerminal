// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.theme.ColorMath
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

/** The colors of the bars around the terminal, as opaque ARGB ints. */
data class ChromeColors(
    /** Background of the tab bar. */
    val surface: Int,
    /** Background of the selected tab. */
    val selected: Int,
    val onSurface: Int,
    /** Border between the panes, and of the divider handles. */
    val outline: Int,
    /** A sticky key that is armed, and the focus outline of a pane. */
    val accent: Int,
    val onAccent: Int
)

/** Derives the chrome from a terminal scheme, so the tabs and the keys share its palette. */
object ChromeColorsFor {
    private const val SURFACE_MIX = 0.07f
    private const val SELECTED_MIX = 0.18f
    private const val OUTLINE_MIX = 0.30f
    private const val BLUE = 4
    private const val TEXT_MINIMUM = 4.5
    private const val BLACK = TerminalColorScheme.BLACK
    private const val WHITE = -0x1 // 0xffffffff

    fun scheme(scheme: TerminalColorScheme): ChromeColors {
        val accent = scheme.ansi[BLUE]
        val surface = ColorMath.blend(scheme.background, scheme.foreground, SURFACE_MIX)
        val selected = ColorMath.blend(scheme.background, scheme.foreground, SELECTED_MIX)
        return ChromeColors(
            surface = surface,
            selected = selected,
            // The scheme's own text, pushed towards black or white only if the bar made it too faint
            // (Solarized's text is low contrast by design).
            onSurface = ColorMath.readableOn(
                scheme.foreground,
                listOf(surface, selected),
                TEXT_MINIMUM
            ),
            outline = ColorMath.blend(scheme.background, scheme.foreground, OUTLINE_MIX),
            accent = accent,
            // Black text on a light accent, white on a dark one.
            onAccent = if (ColorMath.luminance(accent) >
                ColorMath.DARK_LIGHT_BOUNDARY
            ) {
                BLACK
            } else {
                WHITE
            }
        )
    }
}
