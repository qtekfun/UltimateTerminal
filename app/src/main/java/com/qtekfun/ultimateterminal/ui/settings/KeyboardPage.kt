// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.settings.SettingsPage
import com.qtekfun.ultimateterminal.domain.terminal.KeyboardType
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
    val typeHeader = stringResource(R.string.settings_keyboard_type_header)
    val typeFooter = stringResource(R.string.settings_keyboard_type_footer)
    SettingsPage(stringResource(R.string.settings_section_keyboard), nav.backLabel, nav.back) {
        section(typeHeader, typeFooter) {
            KeyboardType.entries.forEachIndexed { index, type ->
                IosListRow(
                    title = stringResource(keyboardTypeLabel(type)),
                    accessory = if (type == settings.keyboardType) {
                        IosAccessory.Check
                    } else {
                        IosAccessory.None
                    },
                    showSeparator = index != KeyboardType.entries.lastIndex,
                    onClick = { viewModel.setKeyboardType(type) }
                )
            }
        }
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

private fun keyboardTypeLabel(type: KeyboardType): Int = when (type) {
    KeyboardType.NORMAL -> R.string.settings_keyboard_type_normal
    KeyboardType.COMPATIBLE -> R.string.settings_keyboard_type_compatible
    KeyboardType.RAW -> R.string.settings_keyboard_type_raw
}
