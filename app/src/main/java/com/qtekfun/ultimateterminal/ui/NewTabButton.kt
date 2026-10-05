// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.DistroOption
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosIcon
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** Which control the new-tab menu hangs from, so that it opens next to the one that was used. */
private enum class NewTabMenuSource { PLUS, MORE }

/**
 * The "+" and, next to it, the explicit "more options" button: a tap on "+" opens a tab of the default
 * distro, and the button (or a long press on "+", kept as a shortcut) opens the menu. Stacked in the
 * rail, which is 56 dp wide, and side by side elsewhere. The button is not a focus target (D-T26-7).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewTabButton(
    choices: List<DistroOption>,
    onNewTab: () -> Unit,
    onNewTabIn: (Long?) -> Unit,
    links: TabBarLinks,
    stacked: Boolean
) {
    var menuFrom by remember { mutableStateOf<NewTabMenuSource?>(null) }
    val label = stringResource(R.string.tab_new)
    val chooseLabel = stringResource(R.string.tab_new_choose)
    val plus: @Composable () -> Unit = {
        Box(
            Modifier
                .size(TouchSize)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                }
                .combinedClickable(
                    onClickLabel = label,
                    onClick = onNewTab,
                    onLongClickLabel = chooseLabel,
                    onLongClick = { menuFrom = NewTabMenuSource.PLUS }
                ),
            contentAlignment = Alignment.Center
        ) {
            IosIcon(IosGlyph.PLUS, null, tint = IosTheme.colors.tint)
            NewTabMenu(
                menuFrom == NewTabMenuSource.PLUS,
                { menuFrom = null },
                choices,
                onNewTabIn,
                links
            )
        }
    }
    val more: @Composable () -> Unit = {
        MoreOptionsButton(
            menuOpen = menuFrom != null,
            onOpen = { menuFrom = NewTabMenuSource.MORE },
            chooseLabel = chooseLabel
        ) {
            NewTabMenu(
                menuFrom == NewTabMenuSource.MORE,
                { menuFrom = null },
                choices,
                onNewTabIn,
                links
            )
        }
    }
    if (stacked) {
        Column {
            plus()
            more()
        }
    } else {
        Row {
            plus()
            more()
        }
    }
}

/** The "more options" button: the three dots, with the state of the menu for a screen reader. */
@Composable
private fun MoreOptionsButton(
    menuOpen: Boolean,
    onOpen: () -> Unit,
    chooseLabel: String,
    menu: @Composable () -> Unit
) {
    val text = stringResource(R.string.tab_new_more)
    val state = stringResource(
        if (menuOpen) R.string.tab_new_more_open else R.string.tab_new_more_closed
    )
    Box(
        Modifier
            .size(TouchSize)
            .semantics {
                contentDescription = text
                stateDescription = state
                role = Role.Button
                onClick(label = chooseLabel) {
                    onOpen()
                    true
                }
            }
            // A tap gesture and a semantic action, not `clickable`, so it never takes the keyboard.
            .pointerInput(Unit) { detectTapGestures { onOpen() } },
        contentAlignment = Alignment.Center
    ) {
        IosIcon(IosGlyph.ELLIPSIS, null, tint = IosTheme.colors.tint, size = MenuIconSize)
        menu()
    }
}
