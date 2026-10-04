// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeResolverTest {
    @Test
    fun theSystemModeFollowsTheSystem() {
        assertEquals(
            ThemeDecision(dark = true, oled = false),
            resolveTheme(ThemeMode.SYSTEM, true, false)
        )
        assertEquals(
            ThemeDecision(dark = false, oled = false),
            resolveTheme(ThemeMode.SYSTEM, false, false)
        )
    }

    @Test
    fun anExplicitModeIgnoresTheSystem() {
        assertEquals(
            false,
            resolveTheme(ThemeMode.LIGHT, systemDark = true, oledBlack = false).dark
        )
        assertEquals(true, resolveTheme(ThemeMode.DARK, systemDark = false, oledBlack = false).dark)
    }

    @Test
    fun oledOnlyAppliesWhileTheThemeIsDark() {
        assertEquals(true, resolveTheme(ThemeMode.DARK, false, true).oled)
        assertEquals(true, resolveTheme(ThemeMode.SYSTEM, true, true).oled)
        assertEquals(false, resolveTheme(ThemeMode.SYSTEM, false, true).oled)
        assertEquals(false, resolveTheme(ThemeMode.LIGHT, true, true).oled)
        assertEquals(false, resolveTheme(ThemeMode.DARK, true, false).oled)
    }
}
