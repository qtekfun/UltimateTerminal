// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.settings.ShortcutDisplay
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import com.qtekfun.ultimateterminal.ui.ios.IosListRow

private const val CHORD_SEPARATOR = "   "

/** The keyboard shortcuts, as a reference: they cannot be changed yet. */
@Composable
internal fun ShortcutsPage(nav: PageNav) {
    val rows = remember { ShortcutDisplay.rows(ShortcutMap.defaults()) }
    val footer = stringResource(R.string.settings_shortcuts_footer)
    SettingsPage(stringResource(R.string.settings_shortcuts), nav.backLabel, nav.back) {
        section(footer = footer) {
            rows.forEachIndexed { index, row ->
                IosListRow(
                    title = shortcutLabel(row.shortcut),
                    subtitle = row.chords.joinToString(CHORD_SEPARATOR),
                    showSeparator = index != rows.lastIndex
                )
            }
        }
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
private fun shortcutLabel(shortcut: AppShortcut): String = when {
    shortcut is AppShortcut.SelectTab ->
        stringResource(R.string.shortcut_select_tab, "1–${AppShortcut.MAX_DIRECT_TAB}")

    else -> labels[shortcut]?.let { stringResource(it) } ?: shortcut.id
}
