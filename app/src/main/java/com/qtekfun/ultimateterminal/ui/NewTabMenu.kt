// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.DistroOption

/** Where a new tab can open, and the other screens: the split, SSH, distros and the appearance. */
@Composable
internal fun NewTabMenu(
    open: Boolean,
    close: () -> Unit,
    choices: List<DistroOption>,
    onNewTabIn: (Long?) -> Unit,
    links: TabBarLinks
) {
    DropdownMenu(expanded = open, onDismissRequest = close) {
        choices.forEach { choice ->
            MenuLink(choice.name ?: stringResource(R.string.tab_shell_option), close) {
                onNewTabIn(choice.id)
            }
        }
        // The split of the pane that has the keyboard: here, and not as a button over the text.
        MenuLink(stringResource(R.string.pane_split_right), close, links.splitRight)
        MenuLink(stringResource(R.string.pane_split_down), close, links.splitDown)
        MenuLink(stringResource(R.string.ssh_open), close, links.openSsh)
        MenuLink(stringResource(R.string.tab_manage_distros), close, links.openDistros)
        MenuLink(stringResource(R.string.appearance_open), close, links.openAppearance)
    }
}

@Composable
private fun MenuLink(text: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = {
            close()
            action()
        }
    )
}
