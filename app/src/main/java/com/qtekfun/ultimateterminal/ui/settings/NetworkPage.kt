// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.settings.DnsServers
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

/** The DNS servers used when the device reports none. */
@Composable
internal fun NetworkPage(settings: AppSettings, viewModel: SettingsViewModel, nav: PageNav) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val servers = settings.dnsFallbackServers
    val shown = if (DnsServers.isDefault(servers)) {
        stringResource(R.string.settings_dns_builtin)
    } else {
        DnsServers.format(servers)
    }
    val footer = stringResource(R.string.settings_dns_footer)
    SettingsPage(stringResource(R.string.settings_section_network), nav.backLabel, nav.back) {
        section(footer = footer) {
            IosListRow(
                title = stringResource(R.string.settings_dns),
                accessory = IosAccessory.Value(shown, chevron = true),
                showSeparator = false,
                onClick = { editing = true }
            )
        }
    }
    if (editing) {
        DnsSheet(servers, onSave = viewModel::setDnsServers, onDismiss = { editing = false })
    }
}
