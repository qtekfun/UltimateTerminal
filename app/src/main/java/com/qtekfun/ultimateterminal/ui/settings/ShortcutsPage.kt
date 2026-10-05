// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.settings.ShortcutDisplay
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.settings.SettingsViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

private const val CHORD_SEPARATOR = "   "

/**
 * The keyboard shortcuts (SPEC RF-12): every action with its key combinations. Tapping one opens
 * the sheet where its combinations are added and removed. The tab numbers are one row that shows
 * the nine and cannot be changed yet.
 */
@Composable
internal fun ShortcutsPage(settings: AppSettings, viewModel: SettingsViewModel, nav: PageNav) {
    val rows = ShortcutDisplay.allRows(settings.shortcuts)
    var editing by remember { mutableStateOf<AppShortcut?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val footer = stringResource(R.string.settings_shortcuts_footer)
    val none = stringResource(R.string.shortcuts_none)
    val tabsFooter = stringResource(R.string.shortcuts_tabs_fixed)
    SettingsPage(stringResource(R.string.settings_shortcuts), nav.backLabel, nav.back) {
        section(footer = footer) {
            rows.forEachIndexed { index, row ->
                val editable = row.shortcut !is AppShortcut.SelectTab
                IosListRow(
                    title = shortcutLabel(row.shortcut),
                    subtitle = row.chords.joinToString(CHORD_SEPARATOR).ifEmpty { none },
                    accessory = if (editable) IosAccessory.Chevron else IosAccessory.None,
                    showSeparator = index != rows.lastIndex,
                    onClick = if (editable) {
                        { editing = row.shortcut }
                    } else {
                        null
                    }
                )
            }
        }
        section(footer = tabsFooter) {
            IosListRow(
                title = stringResource(R.string.shortcuts_reset),
                destructive = true,
                showSeparator = false,
                onClick = { confirmReset = true }
            )
        }
    }
    editing?.let { shortcut ->
        ShortcutSheet(shortcut, settings.shortcuts, viewModel) { editing = null }
    }
    if (confirmReset) {
        IosAlert(
            title = stringResource(R.string.shortcuts_reset_title),
            message = stringResource(R.string.shortcuts_reset_body),
            actions = listOf(
                IosAction(stringResource(R.string.dialog_cancel), IosActionRole.CANCEL),
                IosAction(
                    stringResource(R.string.shortcuts_reset),
                    IosActionRole.DESTRUCTIVE,
                    viewModel::resetShortcuts
                )
            ),
            onDismiss = { confirmReset = false }
        )
    }
}

private val labels: Map<AppShortcut, Int> = mapOf(
    AppShortcut.NewTab to R.string.shortcut_new_tab,
    AppShortcut.CloseTab to R.string.shortcut_close_tab,
    AppShortcut.NextTab to R.string.shortcut_next_tab,
    AppShortcut.PreviousTab to R.string.shortcut_previous_tab,
    AppShortcut.SplitHorizontal to R.string.shortcut_split_horizontal,
    AppShortcut.SplitVertical to R.string.shortcut_split_vertical,
    AppShortcut.ClosePane to R.string.shortcut_close_pane,
    AppShortcut.ToggleZoom to R.string.shortcut_toggle_zoom,
    AppShortcut.FocusLeft to R.string.shortcut_focus_left,
    AppShortcut.FocusRight to R.string.shortcut_focus_right,
    AppShortcut.FocusUp to R.string.shortcut_focus_up,
    AppShortcut.FocusDown to R.string.shortcut_focus_down,
    AppShortcut.ToggleBroadcast to R.string.shortcut_toggle_broadcast,
    AppShortcut.SaveLayout to R.string.shortcut_save_layout,
    AppShortcut.OpenLayouts to R.string.shortcut_open_layouts,
    AppShortcut.Copy to R.string.shortcut_copy,
    AppShortcut.Paste to R.string.shortcut_paste,
    AppShortcut.ZoomIn to R.string.shortcut_zoom_in,
    AppShortcut.ZoomOut to R.string.shortcut_zoom_out,
    AppShortcut.ZoomReset to R.string.shortcut_zoom_reset
)

/** The name of a shortcut in the user's language; the tab numbers are one line, "1–9". */
@Composable
internal fun shortcutLabel(shortcut: AppShortcut): String = when {
    shortcut is AppShortcut.SelectTab ->
        stringResource(R.string.shortcut_select_tab, "1–${AppShortcut.MAX_DIRECT_TAB}")

    else -> labels[shortcut]?.let { stringResource(it) } ?: shortcut.id
}
