// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.session.SidebarMode
import com.qtekfun.ultimateterminal.domain.settings.ScrollbackChoices
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import java.text.NumberFormat

/** How much history each terminal keeps. */
@Composable
internal fun TerminalPage(settings: AppSettings, viewModel: SettingsViewModel, nav: PageNav) {
    val selected = ScrollbackChoices.nearest(settings.defaultScrollbackLines)
    val format = remember { NumberFormat.getIntegerInstance() }
    val header = stringResource(R.string.settings_scrollback_header)
    val footer = stringResource(R.string.settings_scrollback_footer)
    val sidebarHeader = stringResource(R.string.settings_sidebar_header)
    val sidebarFooter = stringResource(R.string.settings_sidebar_footer)
    SettingsPage(stringResource(R.string.settings_section_terminal), nav.backLabel, nav.back) {
        section(sidebarHeader, sidebarFooter) {
            IosListRow(
                title = stringResource(R.string.settings_sidebar_collapse),
                accessory = IosAccessory.Toggle(
                    settings.sidebarMode == SidebarMode.AUTO_COLLAPSE
                ) { collapse ->
                    viewModel.setSidebarMode(
                        if (collapse) SidebarMode.AUTO_COLLAPSE else SidebarMode.ALWAYS_EXPANDED
                    )
                },
                showSeparator = false
            )
        }
        section(header, footer) {
            ScrollbackChoices.options.forEachIndexed { index, lines ->
                IosListRow(
                    title = stringResource(
                        R.string.settings_scrollback_option,
                        format.format(lines)
                    ),
                    accessory = if (lines == selected) IosAccessory.Check else IosAccessory.None,
                    showSeparator = index != ScrollbackChoices.options.lastIndex,
                    onClick = { viewModel.setScrollback(lines) }
                )
            }
        }
    }
}
