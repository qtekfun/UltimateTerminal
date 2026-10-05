// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.settings.SettingsNavigation
import com.qtekfun.ultimateterminal.domain.settings.SettingsPage
import com.qtekfun.ultimateterminal.domain.settings.parent
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** The screens that Settings opens on top of itself instead of showing inside it. */
class SettingsLinks(
    val openAppearance: () -> Unit,
    val openDistros: () -> Unit,
    val openProfiles: () -> Unit,
    val openLayouts: () -> Unit
)

/** What a page needs to move around: where its back button goes and how to open another page. */
internal class PageNav(
    val backLabel: String,
    val back: () -> Unit,
    val open: (SettingsPage) -> Unit,
    val close: () -> Unit
)

private val NavigationSaver = listSaver<SettingsNavigation, String>(
    save = { it.toNames() },
    restore = { SettingsNavigation.fromNames(it) }
)

/**
 * The settings screen (SPEC RF-11): a list of sections and, inside each, its own page, in the iOS
 * style. The back button, and the system one, go up one page and from the root close the screen.
 * Appearance and Distributions are screens of their own, opened through [links].
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    links: SettingsLinks,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var navigation by rememberSaveable(stateSaver = NavigationSaver) {
        mutableStateOf(SettingsNavigation())
    }
    BackHandler { if (navigation.canGoBack) navigation = navigation.back() else onClose() }
    val stored = settings
    if (stored == null) {
        // Nothing to show until the stored settings arrive; the page is not drawn half-empty.
        Box(Modifier.fillMaxSize().background(IosTheme.colors.groupedBackground))
        return
    }
    val page = navigation.current
    val nav = PageNav(
        backLabel = stringResource(titleOf(page.parent)),
        back = { navigation = navigation.back() },
        open = { navigation = navigation.open(it) },
        close = onClose
    )
    PageContent(page, stored, viewModel, links, nav)
}

@Composable
private fun PageContent(
    page: SettingsPage,
    stored: AppSettings,
    viewModel: SettingsViewModel,
    links: SettingsLinks,
    nav: PageNav
) {
    when (page) {
        SettingsPage.ROOT -> RootPage(links, nav)
        SettingsPage.TERMINAL -> TerminalPage(stored, viewModel, nav)
        SettingsPage.KEYBOARD -> KeyboardPage(stored, viewModel, nav)
        SettingsPage.KEYBOARD_KEYS -> KeyboardKeysPage(stored, viewModel, nav)
        SettingsPage.SHORTCUTS -> ShortcutsPage(stored, viewModel, nav)
        SettingsPage.SESSIONS -> SessionsPage(stored, viewModel, nav)
        SettingsPage.DISTROS -> DistrosPage(links, nav)
        SettingsPage.STORAGE -> StoragePage(nav)
        SettingsPage.NETWORK -> NetworkPage(stored, viewModel, nav)
        SettingsPage.BACKUP -> BackupPage(nav)
        SettingsPage.ABOUT -> AboutPage(nav)
        SettingsPage.NOTICES -> NoticesPage(nav)
    }
}

/** The title of a page, which is also the label of the back button of the pages inside it. */
@StringRes
internal fun titleOf(page: SettingsPage): Int = when (page) {
    SettingsPage.ROOT -> R.string.settings_title
    SettingsPage.TERMINAL -> R.string.settings_section_terminal
    SettingsPage.KEYBOARD -> R.string.settings_section_keyboard
    SettingsPage.KEYBOARD_KEYS -> R.string.settings_keys_customize
    SettingsPage.SHORTCUTS -> R.string.settings_shortcuts
    SettingsPage.SESSIONS -> R.string.settings_section_sessions
    SettingsPage.DISTROS -> R.string.settings_section_distros
    SettingsPage.STORAGE -> R.string.settings_section_storage
    SettingsPage.NETWORK -> R.string.settings_section_network
    SettingsPage.BACKUP -> R.string.settings_section_backup
    SettingsPage.ABOUT -> R.string.settings_section_about
    SettingsPage.NOTICES -> R.string.settings_about_credits
}
