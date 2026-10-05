// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

import com.qtekfun.ultimateterminal.domain.appearance.CustomFont
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.launch.ResolvConf
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
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
    /**
     * Run proot without its seccomp filter: slower, but a way out on a kernel where the filter makes
     * it misbehave. Off by default; it is the first thing to try if a distro does not start.
     */
    val prootCompatibilityMode: Boolean = false,
    val defaultScrollbackLines: Int = Profile.DEFAULT_SCROLLBACK,
    /** The id of the terminal color scheme in use: a built-in one or an imported one. */
    val terminalSchemeId: String = BuiltInSchemes.DEFAULT_ID,
    /** The terminal font size in sp, as the pinch zoom and the shortcuts left it. */
    val terminalFontSizeSp: Float = FontZoom.DEFAULT_SP,
    /** The color schemes the user imported. */
    val customSchemes: List<TerminalColorScheme> = emptyList(),
    /** Font, spacing, margin, cursor and the style of the bars (T12c). */
    val appearance: TerminalAppearance = TerminalAppearance(),
    /** The fonts the user imported; their files are in the app's private font folder. */
    val customFonts: List<CustomFont> = emptyList(),
    /** The keys of the extra-keys row and whether it follows the keyboard (SPEC RF-08). */
    val extraKeys: ExtraKeysConfig = ExtraKeysConfig.default(),
    /** DNS servers used only when the device reports none (SPEC RF-11, Network). */
    val dnsFallbackServers: List<String> = ResolvConf.FALLBACK_SERVERS,
    /** The application shortcuts: which key combination does what (SPEC RF-12). */
    val shortcuts: ShortcutMap = ShortcutMap.defaults()
)
