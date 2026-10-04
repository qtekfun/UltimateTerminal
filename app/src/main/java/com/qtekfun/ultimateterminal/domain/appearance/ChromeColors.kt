// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.theme.ColorMath
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

/** The colors of the bars around the terminal, as opaque ARGB ints. */
data class ChromeColors(
    /** Background of the tab bar and of the extra keys. */
    val surface: Int,
    /** Background of the selected tab. */
    val selected: Int,
    val onSurface: Int,
    /** Border between the panes, and of the divider handles. */
    val outline: Int,
    /** A sticky key that is armed, and the focus outline of a pane. */
    val accent: Int,
    val onAccent: Int,
    /** The cap of an extra key, a step lighter than the tray it sits on, as on a phone keyboard. */
    val key: Int,
    /** The cap while it is pressed. */
    val keyPressed: Int,
    val onKey: Int
)

/** Derives the chrome from a terminal scheme, so the tabs and the keys share its palette. */
object ChromeColorsFor {
    private const val SURFACE_MIX = 0.07f
    private const val SELECTED_MIX = 0.18f
    private const val OUTLINE_MIX = 0.30f
    private const val KEY_MIX = 0.16f
    private const val KEY_PRESSED_MIX = 0.22f
    private const val BLUE = 4
    private const val TEXT_MINIMUM = 4.5
    private const val BLACK = TerminalColorScheme.BLACK
    private const val WHITE = -0x1 // 0xffffffff

    fun scheme(scheme: TerminalColorScheme): ChromeColors {
        val accent = scheme.ansi[BLUE]
        val surface = ColorMath.blend(scheme.background, scheme.foreground, SURFACE_MIX)
        val selected = ColorMath.blend(scheme.background, scheme.foreground, SELECTED_MIX)
        // A key is lighter than its tray, in a light scheme as in a dark one, and darker when pressed.
        val backgroundIsLighter =
            ColorMath.luminance(scheme.background) > ColorMath.luminance(scheme.foreground)
        val lighter = if (backgroundIsLighter) scheme.background else scheme.foreground
        val darker = if (backgroundIsLighter) scheme.foreground else scheme.background
        val key = ColorMath.blend(surface, lighter, KEY_MIX)
        val keyPressed = ColorMath.blend(key, darker, KEY_PRESSED_MIX)
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
            },
            key = key,
            keyPressed = keyPressed,
            onKey = ColorMath.readableOn(scheme.foreground, listOf(key, keyPressed), TEXT_MINIMUM)
        )
    }
}
