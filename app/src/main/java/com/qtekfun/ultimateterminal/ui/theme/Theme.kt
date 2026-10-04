// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision

@Composable
fun UltimateTerminalTheme(
    decision: ThemeDecision,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic -> {
            val context = LocalContext.current
            if (decision.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        decision.dark -> darkColorScheme()

        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = if (decision.oled) base.oled() else base, content = content)
}

/**
 * The scheme in OLED mode: the surfaces that fill the screen are pure black so those pixels are
 * off. The brightest containers keep their tone, so raised elements (menus, dialogs) stay visible.
 */
internal fun ColorScheme.oled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black
)
