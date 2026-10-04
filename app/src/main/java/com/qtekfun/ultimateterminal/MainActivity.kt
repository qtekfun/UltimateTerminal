// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.domain.theme.resolveTheme
import com.qtekfun.ultimateterminal.terminal.SessionManager
import com.qtekfun.ultimateterminal.ui.DistroScreen
import com.qtekfun.ultimateterminal.ui.SessionPrompts
import com.qtekfun.ultimateterminal.ui.SshScreen
import com.qtekfun.ultimateterminal.ui.TerminalScreen
import com.qtekfun.ultimateterminal.ui.theme.UltimateTerminalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Nothing is drawn until the stored settings arrive, so the terminal never starts
            // with the default colors and font size and then jumps to the saved ones.
            val settings by produceState<AppSettings?>(null) {
                settingsRepository.observe().collect { value = it }
            }
            settings?.let { Content(it) }
        }
    }

    @Composable
    private fun Content(settings: AppSettings) {
        val decision = resolveTheme(settings.themeMode, isSystemInDarkTheme(), settings.oledBlack)
        val scheme = remember(settings.terminalSchemeId, settings.customSchemes, decision.oled) {
            SchemeCatalog.resolve(settings.terminalSchemeId, settings.customSchemes)
                .let { if (decision.oled) it.forOled() else it }
        }
        // The terminal is drawn under the system bars, so their icons follow the scheme's
        // background, not the system theme.
        SideEffect {
            val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { scheme.isDark }
            enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        }
        UltimateTerminalTheme(decision, settings.dynamicColor) {
            Screens(scheme, settings.terminalFontSizeSp)
        }
    }

    @Composable
    private fun Screens(scheme: TerminalColorScheme, fontSizeSp: Float) {
        val scope = rememberCoroutineScope()
        val sessions by sessionManager.state.collectAsStateWithLifecycle()
        var hadSessions by remember { mutableStateOf(false) }
        var showDistros by rememberSaveable { mutableStateOf(false) }
        var showSsh by rememberSaveable { mutableStateOf(false) }
        // "Exit" in the notification closes every session: close the screen with them.
        LaunchedEffect(sessions.items.isEmpty()) {
            if (sessions.items.isNotEmpty()) {
                hadSessions = true
            } else if (hadSessions) {
                finishAndRemoveTask()
            }
        }
        Box {
            TerminalScreen(
                scheme = scheme,
                initialFontSizeSp = fontSizeSp,
                onFontSizeChanged = { size ->
                    scope.launch {
                        settingsRepository.update { it.copy(terminalFontSizeSp = size) }
                    }
                }
            )
            // Temporary entry points to the distro (T07) and SSH (T14) screens: they belong in the
            // tab bar's "new tab" menu, which T09 owns; see DECISIONS.md, D-T07-7 and D-T14.
            Row(modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()) {
                TextButton(onClick = { showSsh = true }) { Text(stringResource(R.string.ssh_open)) }
                TextButton(onClick = {
                    showDistros = true
                }) { Text(stringResource(R.string.distros_open)) }
            }
            if (showDistros) DistroScreen(onClose = { showDistros = false })
            if (showSsh) SshScreen(onClose = { showSsh = false })
        }
        SessionPrompts(hasRunningSession = sessions.needsService)
    }
}
