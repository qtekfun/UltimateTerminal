// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.qtekfun.ultimateterminal.domain.ios.IosPalette
import com.qtekfun.ultimateterminal.domain.ios.iosPalette
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision

/** The colors of an [IosPalette] as Compose colors. */
@Immutable
class IosColors(palette: IosPalette) {
    val background = Color(palette.background)
    val groupedBackground = Color(palette.groupedBackground)
    val cell = Color(palette.cell)
    val pressedCell = Color(palette.pressedCell)
    val label = Color(palette.label)
    val secondaryLabel = Color(palette.secondaryLabel)
    val separator = Color(palette.separator)
    val tint = Color(palette.tint)
    val onTint = Color(palette.onTint)
    val destructive = Color(palette.destructive)
    val switchOn = Color(palette.switchOn)
    val switchOffTrack = Color(palette.switchOffTrack)
    val dark = palette.dark

    /** The fill of search fields, segmented tracks and switch tracks. */
    val fill: Color get() = switchOffTrack
}

internal val LocalIosColors = staticCompositionLocalOf {
    IosColors(iosPalette(ThemeDecision(dark = false, oled = false)))
}

internal val LocalIosTypography = staticCompositionLocalOf { IosTypography.Default }

/** Where the iOS-style components read their colors and text styles. */
object IosTheme {
    val colors: IosColors
        @Composable @ReadOnlyComposable
        get() = LocalIosColors.current

    val typography: IosTypography
        @Composable @ReadOnlyComposable
        get() = LocalIosTypography.current
}

/**
 * Provides the colors and text styles of the iOS-style components. [decision] says light, dark or
 * OLED (see `resolveTheme`); [scheme] gives the tint, so the chrome follows the terminal's colors.
 */
@Composable
fun IosTheme(
    decision: ThemeDecision,
    scheme: TerminalColorScheme? = null,
    content: @Composable () -> Unit
) {
    val colors = remember(decision, scheme) { IosColors(iosPalette(decision, scheme)) }
    CompositionLocalProvider(LocalIosColors provides colors, content = content)
}
