// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide settings; the defaults are what a fresh install uses. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Pure black backgrounds for OLED screens (SPEC RF-10), independent of the dark theme. */
    val oledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    /** Keep the CPU awake while sessions run (SPEC RF-07). */
    val keepAwake: Boolean = false,
    val defaultScrollbackLines: Int = Profile.DEFAULT_SCROLLBACK
)
