// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.session.ExitStep
import com.qtekfun.ultimateterminal.domain.session.Sessions
import com.qtekfun.ultimateterminal.domain.session.StartupTabRule
import com.qtekfun.ultimateterminal.domain.session.exitStep
import com.qtekfun.ultimateterminal.domain.setup.SetupGate
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.domain.theme.ThemeDecision
import com.qtekfun.ultimateterminal.domain.theme.resolveTheme
import com.qtekfun.ultimateterminal.setup.SetupViewModel
import com.qtekfun.ultimateterminal.terminal.SessionManager
import com.qtekfun.ultimateterminal.terminal.TerminalFontLoader
import com.qtekfun.ultimateterminal.ui.AppearanceScreen
import com.qtekfun.ultimateterminal.ui.DistroScreen
import com.qtekfun.ultimateterminal.ui.ExitConfirm
import com.qtekfun.ultimateterminal.ui.LayoutsScreen
import com.qtekfun.ultimateterminal.ui.LocalTerminalCovered
import com.qtekfun.ultimateterminal.ui.ProfileLinks
import com.qtekfun.ultimateterminal.ui.ProfilesScreen
import com.qtekfun.ultimateterminal.ui.SaveLayoutSheet
import com.qtekfun.ultimateterminal.ui.ScreenLinks
import com.qtekfun.ultimateterminal.ui.SessionPrompts
import com.qtekfun.ultimateterminal.ui.SetupScreen
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

    /**
     * No terminal exists until the first-run gate is decided and closed: the first tab (RF-13) opens
     * the default distro, so with no distro the setup (RF-15) comes first. Nothing asks for
     * background permissions on top of it: that prompt needs a running session.
     */
    @Composable
    private fun Screens(
        scheme: TerminalColorScheme,
        settings: AppSettings,
        decision: ThemeDecision
    ) {
        val setup: SetupViewModel = viewModel()
        val gate by setup.gate.collectAsStateWithLifecycle()
        when (gate) {
            SetupGate.UNDECIDED ->
                Box(Modifier.fillMaxSize().background(ComposeColor(scheme.background)))

            SetupGate.SHOWING -> IosTheme(decision, scheme) { SetupScreen(onSkip = setup::skip) }

            SetupGate.CLOSED -> TerminalWithScreens(scheme, settings, decision)
        }
    }

    @Composable
    private fun TerminalWithScreens(
        scheme: TerminalColorScheme,
        settings: AppSettings,
        decision: ThemeDecision
    ) {
        val scope = rememberCoroutineScope()
        val sessions by sessionManager.state.collectAsStateWithLifecycle()
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showDistros by rememberSaveable { mutableStateOf(false) }
        var showSsh by rememberSaveable { mutableStateOf(false) }
        var showAppearance by rememberSaveable { mutableStateOf(false) }
        var askExit by rememberSaveable { mutableStateOf(false) }
        var overlay by rememberSaveable(stateSaver = ProfileOverlay.Saver) {
            mutableStateOf(ProfileOverlay())
        }
        val typefaces = remember(settings.appearance.fontId, settings.customFonts) {
            fontLoader.load(settings.appearance.fontId, settings.customFonts)
        }
        FinishWithSessions(count = sessions.items.size)
        val covered = showSettings || showDistros || showSsh || showAppearance || overlay.any
        Box {
            CompositionLocalProvider(LocalTerminalCovered provides covered) {
                TerminalScreen(
                    look = TerminalLook(scheme, settings.appearance, typefaces),
                    initialFontSizeSp = settings.terminalFontSizeSp,
                    sidebarMode = settings.sidebarMode,
                    keyboardType = settings.keyboardType,
                    onFontSizeChanged = { size -> scope.launch { saveFontSize(size) } },
                    screens = ScreenLinks(
                        openDistros = { showDistros = true },
                        openSsh = { showSsh = true },
                        openAppearance = { showAppearance = true },
                        openSettings = { showSettings = true },
                        requestExit = { requestExit(sessions) { askExit = true } },
                        profiles = overlay.links { overlay = it }
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
                            openDistros = { showDistros = true },
                            openProfiles = { overlay = overlay.copy(profiles = true) },
                            openLayouts = { overlay = overlay.copy(layouts = true) }
                        )
                    )
                }
                if (showDistros) DistroScreen(onClose = { showDistros = false })
                if (showSsh) SshScreen(onClose = { showSsh = false })
                if (showAppearance) AppearanceScreen(onClose = { showAppearance = false })
                ProfileOverlays(overlay, { overlay = it }) {
                    // Opening a profile or a layout shows the new tab: nothing stays over it.
                    overlay = ProfileOverlay()
                    showSettings = false
                }
            }
        }
        Prompts(sessions, askExit, IosThemeArgs(decision, scheme)) { askExit = false }
    }

    /** The prompts over everything: the background permissions and the exit confirmation. */
    @Composable
    private fun Prompts(
        sessions: Sessions,
        askExit: Boolean,
        theme: IosThemeArgs,
        onExitDismissed: () -> Unit
    ) = IosTheme(theme.decision, theme.scheme) {
        SessionPrompts(hasRunningSession = sessions.needsService)
        val step = exitStep(sessions)
        if (askExit && step is ExitStep.Ask) ExitConfirm(step.sessions, ::exitApp, onExitDismissed)
    }

    /** The "Exit" entry: leaves at once with nothing running, else asks through [ask] (D-EXIT-1). */
    private fun requestExit(sessions: Sessions, ask: () -> Unit) {
        when (exitStep(sessions)) {
            ExitStep.ExitNow -> exitApp()
            is ExitStep.Ask -> ask()
        }
    }

    /**
     * Ends everything through the one shutdown path (D-EXIT-2): sessions, service, notification and,
     * through the manager's empty hook, the task. That hook does not fire with no session to close, so
     * the task is also removed here.
     */
    private fun exitApp() {
        sessionManager.shutdownAll()
        finishAndRemoveTask()
    }

    /** Which of the profile screens are open. */
    private data class ProfileOverlay(
        val profiles: Boolean = false,
        val layouts: Boolean = false,
        val saveLayout: Boolean = false
    ) {
        val any: Boolean get() = profiles || layouts || saveLayout

        /** The links that open each of these screens, which [onShown] stores. */
        fun links(onShown: (ProfileOverlay) -> Unit) = ProfileLinks(
            openProfiles = { onShown(copy(profiles = true)) },
            openLayouts = { onShown(copy(layouts = true)) },
            saveLayout = { onShown(copy(saveLayout = true)) }
        )

        companion object {
            val Saver = listSaver<ProfileOverlay, Boolean>(
                save = { listOf(it.profiles, it.layouts, it.saveLayout) },
                restore = { ProfileOverlay(it[0], it[1], it[2]) }
            )
        }
    }

    /** Profiles, layouts and the save-layout sheet; opening a profile or layout closes it all. */
    @Composable
    private fun ProfileOverlays(
        shown: ProfileOverlay,
        onShown: (ProfileOverlay) -> Unit,
        onOpened: () -> Unit
    ) {
        if (shown.profiles) {
            ProfilesScreen(onClose = { onShown(shown.copy(profiles = false)) }, onOpened = onOpened)
        }
        if (shown.layouts) {
            LayoutsScreen(onClose = { onShown(shown.copy(layouts = false)) }, onOpened = onOpened)
        }
        if (shown.saveLayout) {
            val close = { onShown(shown.copy(saveLayout = false)) }
            SaveLayoutSheet(close, close)
        }
    }

    private suspend fun saveFontSize(size: Float) =
        settingsRepository.update { it.copy(terminalFontSizeSp = size) }

    /**
     * A stale screen never shows an empty app (RF-13b, D-NOTIF-1). The task is normally removed by
     * [SessionManager] when the last session goes; this covers an activity that outlived it, which
     * finishes itself when it comes back or sees the sessions go, and never opens a tab on its own.
     */
    @Composable
    private fun FinishWithSessions(count: Int) {
        var hadSessions by remember { mutableStateOf(false) }
        LaunchedEffect(count) {
            if (StartupTabRule.onSessionsChanged(count, hadSessions) ==
                StartupTabRule.Action.FINISH
            ) {
                finishAndRemoveTask()
            }
            hadSessions = count > 0
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            val count = sessionManager.state.value.items.size
            if (StartupTabRule.onResume(count, hadSessions) == StartupTabRule.Action.FINISH) {
                finishAndRemoveTask()
            }
        }
    }
}

private class IosThemeArgs(val decision: ThemeDecision, val scheme: TerminalColorScheme)
