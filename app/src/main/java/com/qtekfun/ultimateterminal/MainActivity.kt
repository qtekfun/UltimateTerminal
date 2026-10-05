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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision
import com.qtekfun.ultimateterminal.domain.theme.resolveTheme
import com.qtekfun.ultimateterminal.terminal.SessionManager
import com.qtekfun.ultimateterminal.terminal.TerminalFontLoader
import com.qtekfun.ultimateterminal.ui.AppearanceScreen
import com.qtekfun.ultimateterminal.ui.DistroScreen
import com.qtekfun.ultimateterminal.ui.LocalTerminalCovered
import com.qtekfun.ultimateterminal.ui.ScreenLinks
import com.qtekfun.ultimateterminal.ui.SessionPrompts
import com.qtekfun.ultimateterminal.ui.SshScreen
import com.qtekfun.ultimateterminal.ui.TerminalLook
import com.qtekfun.ultimateterminal.ui.TerminalScreen
import com.qtekfun.ultimateterminal.ui.ios.IosTheme
import com.qtekfun.ultimateterminal.ui.settings.SettingsLinks
import com.qtekfun.ultimateterminal.ui.settings.SettingsScreen
import com.qtekfun.ultimateterminal.ui.theme.UltimateTerminalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var fontLoader: TerminalFontLoader

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
            Screens(scheme, settings, decision)
        }
    }

    @Composable
    private fun Screens(
        scheme: TerminalColorScheme,
        settings: AppSettings,
        decision: ThemeDecision
    ) {
        val scope = rememberCoroutineScope()
        val sessions by sessionManager.state.collectAsStateWithLifecycle()
        var hadSessions by remember { mutableStateOf(false) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showDistros by rememberSaveable { mutableStateOf(false) }
        var showSsh by rememberSaveable { mutableStateOf(false) }
        var showAppearance by rememberSaveable { mutableStateOf(false) }
        val typefaces = remember(settings.appearance.fontId, settings.customFonts) {
            fontLoader.load(settings.appearance.fontId, settings.customFonts)
        }
        // "Exit" in the notification closes every session: close the screen with them.
        LaunchedEffect(sessions.items.isEmpty()) {
            if (sessions.items.isNotEmpty()) {
                hadSessions = true
            } else if (hadSessions) {
                finishAndRemoveTask()
            }
        }
        val covered = showSettings || showDistros || showSsh || showAppearance
        Box {
            CompositionLocalProvider(LocalTerminalCovered provides covered) {
                TerminalScreen(
                    look = TerminalLook(scheme, settings.appearance, typefaces),
                    initialFontSizeSp = settings.terminalFontSizeSp,
                    onFontSizeChanged = { size ->
                        scope.launch {
                            settingsRepository.update { it.copy(terminalFontSizeSp = size) }
                        }
                    },
                    screens = ScreenLinks(
                        openDistros = { showDistros = true },
                        openSsh = { showSsh = true },
                        openAppearance = { showAppearance = true },
                        openSettings = { showSettings = true }
                    )
                )
            }
            // Later is on top: Settings opens Appearance and Distributions over itself.
            IosTheme(decision, scheme) {
                if (showSettings) {
                    SettingsScreen(
                        onClose = { showSettings = false },
                        links = SettingsLinks(
                            openAppearance = { showAppearance = true },
                            openDistros = { showDistros = true }
                        )
                    )
                }
                if (showDistros) DistroScreen(onClose = { showDistros = false })
                if (showSsh) SshScreen(onClose = { showSsh = false })
                if (showAppearance) AppearanceScreen(onClose = { showAppearance = false })
            }
        }
        IosTheme(decision, scheme) { SessionPrompts(hasRunningSession = sessions.needsService) }
    }
}
