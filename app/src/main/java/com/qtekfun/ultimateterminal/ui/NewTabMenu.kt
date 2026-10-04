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
private class NewTabEntry(val label: String, val glyph: IosGlyph, val run: () -> Unit)

/** Where a new tab can open, and the other screens: the split, SSH, distros and the appearance. */
@Composable
internal fun NewTabMenu(
    open: Boolean,
    close: () -> Unit,
    choices: List<DistroOption>,
    onNewTabIn: (Long?) -> Unit,
    links: TabBarLinks
) {
    val shell = stringResource(R.string.tab_shell_option)
    val entries = buildList {
        choices.forEach { choice ->
            add(NewTabEntry(choice.name ?: shell, IosGlyph.TERMINAL) { onNewTabIn(choice.id) })
        }
        // The split of the pane that has the keyboard: here, and not as a button over the text.
        add(
            NewTabEntry(
                stringResource(R.string.pane_split_right),
                IosGlyph.CHEVRON_RIGHT,
                links.splitRight
            )
        )
        add(
            NewTabEntry(
                stringResource(R.string.pane_split_down),
                IosGlyph.CHEVRON_DOWN,
                links.splitDown
            )
        )
        // The settings first among the entries that are not a tab or a split: they are what the
        // user looks for, and Appearance and Distributions are inside them too.
        add(
            NewTabEntry(
                stringResource(R.string.settings_open),
                IosGlyph.SETTINGS,
                links.openSettings
            )
        )
        add(NewTabEntry(stringResource(R.string.ssh_open), IosGlyph.KEY, links.openSsh))
        add(
            NewTabEntry(
                stringResource(R.string.tab_manage_distros),
                IosGlyph.FOLDER,
                links.openDistros
            )
        )
        add(
            NewTabEntry(
                stringResource(R.string.appearance_open),
                IosGlyph.TERMINAL,
                links.openAppearance
            )
        )
    }
    IosContextMenu(expanded = open, onDismiss = close) {
        entries.forEachIndexed { index, entry ->
            IosMenuItem(
                label = entry.label,
                onClick = {
                    close()
                    entry.run()
                },
                glyph = entry.glyph,
                showSeparator = index < entries.lastIndex
            )
        }
    }
}
