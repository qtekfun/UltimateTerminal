// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide settings; the defaults are what a fresh install uses. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Pure black backgrounds for OLED screens (SPEC RF-10), independent of the dark theme. */
    val oledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    /** Keep the CPU awake while sessions run (SPEC RF-07). */
    val keepAwake: Boolean = false,
    /** Mount the device's shared storage in `~/storage` of every distro (SPEC RF-05). Off by default. */
    val sharedStorage: Boolean = false,
    val defaultScrollbackLines: Int = Profile.DEFAULT_SCROLLBACK,
    /** The id of the terminal color scheme in use: a built-in one or an imported one. */
    val terminalSchemeId: String = BuiltInSchemes.DEFAULT_ID,
    /** The terminal font size in sp, as the pinch zoom and the shortcuts left it. */
    val terminalFontSizeSp: Float = FontZoom.DEFAULT_SP,
    /** The color schemes the user imported. */
    val customSchemes: List<TerminalColorScheme> = emptyList()
)
