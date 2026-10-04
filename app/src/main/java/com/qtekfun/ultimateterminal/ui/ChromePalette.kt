// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.domain.appearance.ChromeColorsFor
import com.qtekfun.ultimateterminal.domain.appearance.ChromeStyle
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

/** The colors and the corners of the bars around the terminal: the tabs, the keys, the panes. */
@Immutable
data class ChromePalette(
    val surface: Color,
    val selected: Color,
    val onSurface: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val corner: Dp
)

/** Set by the terminal screen; a bar used outside of it falls back to the system theme. */
val LocalChromePalette = staticCompositionLocalOf<ChromePalette?> { null }

private val DefaultCorner = 8.dp

@Composable
fun currentChrome(): ChromePalette = LocalChromePalette.current ?: materialChrome(DefaultCorner)

@Composable
private fun materialChrome(corner: Dp): ChromePalette {
    val colors = MaterialTheme.colorScheme
    return ChromePalette(
        surface = colors.surface,
        selected = colors.secondaryContainer,
        onSurface = colors.onSurface,
        outline = colors.outline,
        accent = colors.primary,
        onAccent = colors.onPrimary,
        corner = corner
    )
}

/** The palette for [scheme] and [appearance]: derived from the scheme, or from the system theme. */
@Composable
fun rememberChromePalette(
    scheme: TerminalColorScheme,
    appearance: TerminalAppearance
): ChromePalette {
    val corner = appearance.cornerRadiusDp.dp
    val material = materialChrome(corner)
    return remember(scheme, appearance.chromeStyle, corner, material) {
        if (appearance.chromeStyle == ChromeStyle.SCHEME) {
            val chrome = ChromeColorsFor.scheme(scheme)
            ChromePalette(
                surface = Color(chrome.surface),
                selected = Color(chrome.selected),
                onSurface = Color(chrome.onSurface),
                outline = Color(chrome.outline),
                accent = Color(chrome.accent),
                onAccent = Color(chrome.onAccent),
                corner = corner
            )
        } else {
            material
        }
    }
}
