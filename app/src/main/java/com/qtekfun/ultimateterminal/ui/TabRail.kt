// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.TabItem
import com.qtekfun.ultimateterminal.domain.session.tabInitial
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosIcon
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

/** A 48 dp chevron that opens or closes the side bar. */
@Composable
internal fun SidebarButton(glyph: IosGlyph, label: Int, onClick: () -> Unit) {
    val text = stringResource(label)
    Box(
        Modifier
            .size(TouchSize)
            .semantics {
                contentDescription = text
                role = Role.Button
            }
            .clickable(onClickLabel = text, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        IosIcon(glyph, null, tint = currentChrome().onSurface, size = MenuIconSize)
    }
}

/** What a screen reader hears for a tab, and the actions it offers on it. */
@Composable
internal fun tabSpeech(item: TabItem, name: String, count: Int): TabSpeech {
    val state = stringResource(
        if (item.running) R.string.tab_state_running else R.string.tab_state_ended
    )
    return TabSpeech(
        stringResource(R.string.tab_description, name, item.position, count, state),
        stringResource(R.string.tab_rename),
        stringResource(R.string.tab_close),
        stringResource(R.string.tab_select),
        // Dragging is the only touch way to reorder, so a screen reader gets the same as actions.
        if (item.position > 1) stringResource(R.string.tab_move_earlier) else null,
        if (item.position < count) stringResource(R.string.tab_move_later) else null
    )
}

/**
 * A tab of the rail: its initial, with the active one highlighted. A tap selects it; a long press
 * opens the bar, where the rename and close menu and the drag to reorder are. A screen reader
 * hears the same as for the full tab (name, position, state, selected) and has the same actions.
 */
@Composable
internal fun RailTab(
    item: TabItem,
    name: String,
    bar: TabChipBar,
    actions: TabChipActions,
    onNeedBar: () -> Unit
) {
    val speech = tabSpeech(item, name, bar.count)
    Box(
        Modifier
            .size(TouchSize)
            .capsule(if (item.active) currentChrome().selected else Color.Transparent, PillInset)
            .tabSemantics(item.active, speech, actions)
            .pointerInput(item.id) {
                detectTapGestures(onTap = { actions.onSelect() }, onLongPress = { onNeedBar() })
            },
        contentAlignment = Alignment.Center
    ) {
        IosText(
            tabInitial(name),
            maxLines = 1,
            style = IosTheme.typography.subheadline.copy(
                fontWeight = if (item.active) FontWeight.SemiBold else null
            ),
            color = currentChrome().onSurface.copy(alpha = tabAlpha(item))
        )
    }
}

internal fun tabAlpha(item: TabItem): Float = when {
    !item.running -> ENDED_TAB_ALPHA
    item.active -> 1f
    else -> INACTIVE_TAB_ALPHA
}
