// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.TabItem
import com.qtekfun.ultimateterminal.domain.session.TabName
import com.qtekfun.ultimateterminal.domain.session.TabsController
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosAlert

/** A tab's name as text: the one the user gave, its distro (numbered if repeated) or "Shell N". */
@Composable
internal fun tabNameText(name: TabName): String = when (name) {
    is TabName.Custom -> name.text

    is TabName.InProfile -> if (name.ordinal == 1) {
        name.profile
    } else {
        stringResource(R.string.tab_distro_numbered, name.profile, name.ordinal)
    }

    is TabName.InDistro -> if (name.ordinal == 1) {
        name.distro
    } else {
        stringResource(R.string.tab_distro_numbered, name.distro, name.ordinal)
    }

    is TabName.Plain -> stringResource(R.string.tab_default_title, name.number)
}

/**
 * The rename prompt: a centered alert with a text field, as iOS asks for a name. It is the
 * [IosAlert] without its message, so the field sits between the title and the buttons.
 */
@Composable
internal fun RenamePrompt(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    NamePrompt(
        texts = NamePromptTexts(
            title = stringResource(R.string.tab_rename_title),
            label = stringResource(R.string.tab_rename_label),
            hint = stringResource(R.string.tab_rename_hint),
            save = stringResource(R.string.tab_save),
            cancel = stringResource(R.string.tab_cancel)
        ),
        value = text,
        onValueChange = { text = it },
        onSave = { onSave(text) },
        onDismiss = onDismiss
    )
}

@Composable
internal fun CloseConfirm(
    name: String,
    running: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    IosAlert(
        title = stringResource(R.string.tab_close_title, name),
        message = if (running) stringResource(R.string.tab_close_message) else null,
        actions = listOf(
            IosAction(stringResource(R.string.tab_cancel), IosActionRole.CANCEL),
            IosAction(
                stringResource(R.string.tab_close_confirm),
                IosActionRole.DESTRUCTIVE,
                onConfirm
            )
        ),
        onDismiss = onDismiss
    )
}

/** The close confirmation, naming the tab that is waiting for the answer. */
@Composable
internal fun ClosePrompt(
    tabs: TabsController,
    items: List<TabItem>,
    names: List<TabName>,
    id: SessionId?
) {
    val index = items.indexOfFirst { it.id == id }
    if (index >= 0) {
        CloseConfirm(
            name = tabNameText(names[index]),
            running = items[index].running,
            onConfirm = tabs::confirmClose,
            onDismiss = tabs::dismissClose
        )
    }
}
