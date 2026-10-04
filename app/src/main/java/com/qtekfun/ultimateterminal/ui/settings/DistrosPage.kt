// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.storage.ProotOptionsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

/** The way into the distributions and the one option of how they run. */
@Composable
internal fun DistrosPage(
    links: SettingsLinks,
    nav: PageNav,
    options: ProotOptionsViewModel = viewModel()
) {
    val compatibility by options.compatibilityMode.collectAsStateWithLifecycle()
    val manageFooter = stringResource(R.string.settings_distros_footer)
    val compatibilityFooter = stringResource(R.string.proot_compat_explanation)
    SettingsPage(stringResource(R.string.settings_section_distros), nav.backLabel, nav.back) {
        section(footer = manageFooter) {
            Link(R.string.settings_distros_manage, last = true, onClick = links.openDistros)
        }
        section(footer = compatibilityFooter) {
            IosListRow(
                title = stringResource(R.string.proot_compat_label),
                accessory = IosAccessory.Toggle(compatibility, options::setCompatibilityMode),
                showSeparator = false
            )
        }
    }
}
