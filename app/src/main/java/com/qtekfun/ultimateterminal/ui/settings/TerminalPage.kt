// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.language.AppLanguage
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.session.SidebarMode
import com.qtekfun.ultimateterminal.domain.settings.ScrollbackChoices
import com.qtekfun.ultimateterminal.platform.AppLocale
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
    val context = LocalContext.current
    val languageHeader = stringResource(R.string.settings_language_header)
    val languageFooter = stringResource(R.string.settings_language_footer)
    var language by remember { mutableStateOf(AppLocale.current(context)) }
    SettingsPage(stringResource(R.string.settings_section_terminal), nav.backLabel, nav.back) {
        section(languageHeader, languageFooter) {
            AppLanguage.entries.forEachIndexed { index, option ->
                IosListRow(
                    title = stringResource(languageLabel(option)),
                    accessory = if (option == language) IosAccessory.Check else IosAccessory.None,
                    showSeparator = index != AppLanguage.entries.lastIndex,
                    onClick = {
                        if (option != language) {
                            language = option
                            AppLocale.set(context, option)
                            (context as? Activity)?.recreate()
                        }
                    }
                )
            }
        }
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

private fun languageLabel(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.SPANISH -> R.string.settings_language_spanish
}
