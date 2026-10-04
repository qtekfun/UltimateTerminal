// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.settings.SettingsPage
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

/** The extra keys row: whether it shows, when, and the way to arrange its keys. */
@Composable
internal fun KeyboardPage(settings: AppSettings, viewModel: SettingsViewModel, nav: PageNav) {
    val keys = settings.extraKeys
    val header = stringResource(R.string.settings_keys_header)
    val followFooter = stringResource(R.string.settings_keys_follow_footer)
    val shortcutsFooter = stringResource(R.string.settings_shortcuts_footer)
    SettingsPage(stringResource(R.string.settings_section_keyboard), nav.backLabel, nav.back) {
        section(header, followFooter) {
            IosListRow(
                title = stringResource(R.string.settings_keys_show),
                accessory = IosAccessory.Toggle(keys.visible) { on ->
                    viewModel.editExtraKeys { it.copy(visible = on) }
                }
            )
            IosListRow(
                title = stringResource(R.string.settings_keys_follow),
                enabled = keys.visible,
                accessory = IosAccessory.Toggle(keys.onlyWithKeyboard) { on ->
                    viewModel.editExtraKeys { it.copy(onlyWithKeyboard = on) }
                },
                showSeparator = false
            )
        }
        section {
            Link(R.string.settings_keys_customize, last = true) {
                nav.open(SettingsPage.KEYBOARD_KEYS)
            }
        }
        section(footer = shortcutsFooter) {
            Link(R.string.settings_shortcuts, last = true) { nav.open(SettingsPage.SHORTCUTS) }
        }
    }
}
