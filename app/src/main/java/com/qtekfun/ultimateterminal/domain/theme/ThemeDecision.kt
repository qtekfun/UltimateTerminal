// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.qtekfun.ultimateterminal.domain.model.ThemeMode

/** What the settings and the system's own dark mode add up to. */
data class ThemeDecision(val dark: Boolean, val oled: Boolean)

/** OLED mode is a variant of the dark theme: it does nothing while the theme is light. */
fun resolveTheme(mode: ThemeMode, systemDark: Boolean, oledBlack: Boolean): ThemeDecision {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    return ThemeDecision(dark = dark, oled = oledBlack && dark)
}
