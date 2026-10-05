// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.DistroOption
import com.qtekfun.ultimateterminal.ui.ios.IosContextMenu
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosMenuItem

/** One entry of the "+" menu. */
private class NewTabEntry(
    val label: String,
    val glyph: IosGlyph,
    val run: () -> Unit,
    val destructive: Boolean = false
)

/**
 * Where a new tab can open, and the other screens: the split, profiles and layouts, SSH, distros and
 * the appearance.
 */
@Composable
internal fun NewTabMenu(
    open: Boolean,
    close: () -> Unit,
    choices: List<DistroOption>,
    onNewTabIn: (Long?) -> Unit,
    links: TabBarLinks
) {
    val shell = stringResource(R.string.tab_shell_option)
    val entries = choices.map { choice ->
        NewTabEntry(choice.name ?: shell, IosGlyph.TERMINAL, { onNewTabIn(choice.id) })
    } + paneEntries(links) + screenEntries(links.screens) + exitEntry(links.screens)
    IosContextMenu(expanded = open, onDismiss = close) {
        entries.forEachIndexed { index, entry ->
            IosMenuItem(
                label = entry.label,
                onClick = {
                    close()
                    entry.run()
                },
                glyph = entry.glyph,
                destructive = entry.destructive,
                showSeparator = index < entries.lastIndex
            )
        }
    }
}

/** The split of the pane that has the keyboard, here and not as a button over the text, and the layouts. */
@Composable
private fun paneEntries(links: TabBarLinks): List<NewTabEntry> {
    val profiles = links.screens.profiles
    return listOf(
        NewTabEntry(
            stringResource(R.string.pane_split_right),
            IosGlyph.CHEVRON_RIGHT,
            links.splitRight
        ),
        NewTabEntry(
            stringResource(R.string.pane_split_down),
            IosGlyph.CHEVRON_DOWN,
            links.splitDown
        ),
        NewTabEntry(
            stringResource(R.string.pane_profiles),
            IosGlyph.TERMINAL,
            profiles.openProfiles
        ),
        NewTabEntry(stringResource(R.string.pane_layouts), IosGlyph.FOLDER, profiles.openLayouts),
        NewTabEntry(
            stringResource(R.string.pane_save_layout),
            IosGlyph.DOWNLOAD,
            profiles.saveLayout
        )
    )
}

/**
 * The settings first among the entries that are not a tab or a split: they are what the user looks
 * for, and Appearance and Distributions are inside them too.
 */
@Composable
private fun screenEntries(screens: ScreenLinks): List<NewTabEntry> = listOf(
    NewTabEntry(stringResource(R.string.settings_open), IosGlyph.SETTINGS, screens.openSettings),
    NewTabEntry(stringResource(R.string.ssh_open), IosGlyph.KEY, screens.openSsh),
    NewTabEntry(stringResource(R.string.tab_manage_distros), IosGlyph.FOLDER, screens.openDistros),
    NewTabEntry(stringResource(R.string.appearance_open), IosGlyph.TERMINAL, screens.openAppearance)
)

/** Last and in the destructive color: it ends every session and leaves the app. */
@Composable
private fun exitEntry(screens: ScreenLinks) = NewTabEntry(
    stringResource(R.string.exit_open),
    IosGlyph.POWER,
    screens.requestExit,
    destructive = true
).let(::listOf)
