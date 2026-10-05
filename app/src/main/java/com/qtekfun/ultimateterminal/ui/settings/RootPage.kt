// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.settings.SettingsPage
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

/** The list of sections. Done closes Settings. */
@Composable
internal fun RootPage(links: SettingsLinks, nav: PageNav) {
    IosLargeTitleScreen(
        title = stringResource(R.string.settings_title),
        trailing = { IosBarButton(stringResource(R.string.settings_done), nav.close, bold = true) }
    ) {
        section {
            Link(R.string.settings_section_appearance, onClick = links.openAppearance)
            Link(R.string.settings_section_terminal) { nav.open(SettingsPage.TERMINAL) }
            Link(R.string.settings_section_keyboard, last = true) {
                nav.open(SettingsPage.KEYBOARD)
            }
        }
        section {
            Link(R.string.settings_profiles, onClick = links.openProfiles)
            Link(R.string.settings_layouts, last = true, onClick = links.openLayouts)
        }
        section {
            Link(R.string.settings_section_sessions) { nav.open(SettingsPage.SESSIONS) }
            Link(R.string.settings_section_distros) { nav.open(SettingsPage.DISTROS) }
            Link(R.string.settings_section_storage) { nav.open(SettingsPage.STORAGE) }
            Link(R.string.settings_section_network, last = true) { nav.open(SettingsPage.NETWORK) }
        }
        section {
            Link(R.string.settings_section_backup, last = true) { nav.open(SettingsPage.BACKUP) }
        }
        section {
            Link(R.string.settings_section_about, last = true) { nav.open(SettingsPage.ABOUT) }
        }
    }
}

/** A row that opens another page or screen. */
@Composable
internal fun Link(titleRes: Int, last: Boolean = false, onClick: () -> Unit) {
    IosListRow(
        title = stringResource(titleRes),
        accessory = IosAccessory.Chevron,
        showSeparator = !last,
        onClick = onClick
    )
}
