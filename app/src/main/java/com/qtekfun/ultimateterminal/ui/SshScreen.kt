// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.ssh.SshUiState
import com.qtekfun.ultimateterminal.ssh.SshViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosBarIconButton
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection

/** The SSH hosts screen (SPEC RF-09): saved servers, one tap to connect, and the key manager. */
@Composable
fun SshScreen(onClose: () -> Unit, viewModel: SshViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showKeys by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HostDialog?>(null) }
    if (showKeys) {
        SshKeysScreen(onBack = { showKeys = false })
        return
    }
    BackHandler(onBack = onClose)
    val addLabel = stringResource(R.string.ssh_add_host)
    IosLargeTitleScreen(
        title = stringResource(R.string.ssh_title),
        leading = { IosBarButton(stringResource(R.string.ssh_close), onClose) },
        trailing = {
            IosBarIconButton(IosGlyph.PLUS, addLabel, { dialog = HostDialog.Edit(null) })
        }
    ) {
        if (state.busy) item { BusyBar() }
        state.message?.let { message ->
            item { SshMessageBar(message, viewModel::dismissMessage) }
        }
        item {
            IosSection {
                IosListRow(
                    title = stringResource(R.string.ssh_keys_open),
                    glyph = IosGlyph.KEY,
                    accessory = IosAccessory.Chevron,
                    showSeparator = false,
                    onClick = { showKeys = true }
                )
            }
        }
        item {
            HostList(
                state,
                onAdd = { dialog = HostDialog.Edit(null) },
                onOpen = { dialog = HostDialog.Actions(it) }
            )
        }
    }
    HostDialogHost(
        dialog = dialog,
        state = state,
        viewModel = viewModel,
        navigation = HostNavigation(
            show = { dialog = it },
            // The action sheet closes itself after an action ran, and the action may have opened
            // the next dialog: only close what is still the sheet, reading the state as it is now.
            closeActions = { if (dialog is HostDialog.Actions) dialog = null },
            closeScreen = onClose
        )
    )
}

internal sealed interface HostDialog {
    /** [host] is null when adding a new one. */
    data class Edit(val host: SshHost?) : HostDialog

    data class Actions(val host: SshHost) : HostDialog

    data class Delete(val host: SshHost) : HostDialog
}

@Composable
private fun HostDialogHost(
    dialog: HostDialog?,
    state: SshUiState,
    viewModel: SshViewModel,
    navigation: HostNavigation
) {
    val show = navigation.show
    val dismiss = { show(null) }
    when (dialog) {
        null -> Unit

        is HostDialog.Edit -> HostEditDialog(
            host = dialog.host,
            keys = state.keys,
            distros = state.distros,
            onSave = {
                viewModel.saveHost(it)
                dismiss()
            },
            onDismiss = dismiss
        )

        is HostDialog.Actions -> HostActions(
            host = dialog.host,
            canConnect = !state.busy,
            actions = HostActionTargets(
                connect = { viewModel.connect(dialog.host.id, onOpened = navigation.closeScreen) },
                edit = { show(HostDialog.Edit(dialog.host)) },
                delete = { show(HostDialog.Delete(dialog.host)) }
            ),
            dismiss = navigation.closeActions
        )

        is HostDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.ssh_delete_host_title, dialog.host.name),
            body = stringResource(R.string.ssh_delete_host_body),
            confirm = stringResource(R.string.ssh_delete),
            onConfirm = {
                viewModel.deleteHost(dialog.host.id)
                dismiss()
            },
            onDismiss = dismiss
        )
    }
}

/** How the dialogs of the hosts screen move between each other and leave the screen. */
private class HostNavigation(
    val show: (HostDialog?) -> Unit,
    val closeActions: () -> Unit,
    val closeScreen: () -> Unit
)

private class HostActionTargets(
    val connect: () -> Unit,
    val edit: () -> Unit,
    val delete: () -> Unit
)

/** What can be done with one host: the sheet that slides up when its row is tapped. */
@Composable
private fun HostActions(
    host: SshHost,
    canConnect: Boolean,
    actions: HostActionTargets,
    dismiss: () -> Unit
) {
    val items = buildList {
        if (canConnect) {
            add(
                IosAction(stringResource(R.string.ssh_connect), onClick = actions.connect)
            )
        }
        add(IosAction(stringResource(R.string.ssh_edit), onClick = actions.edit))
        add(
            IosAction(
                stringResource(R.string.ssh_delete),
                IosActionRole.DESTRUCTIVE,
                actions.delete
            )
        )
    }
    IosActionSheet(
        actions = items,
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = dismiss,
        title = host.name
    )
}

@Composable
private fun HostList(state: SshUiState, onAdd: () -> Unit, onOpen: (SshHost) -> Unit) {
    if (state.hosts.isEmpty()) {
        IosSection(footer = stringResource(R.string.ssh_no_hosts)) {
            IosListRow(
                title = stringResource(R.string.ssh_add_host),
                glyph = IosGlyph.PLUS,
                accessory = IosAccessory.Chevron,
                showSeparator = false,
                onClick = onAdd
            )
        }
    } else {
        IosSection {
            state.hosts.forEachIndexed { index, host ->
                IosListRow(
                    title = host.name,
                    subtitle = stringResource(
                        R.string.ssh_host_summary,
                        host.user,
                        host.host,
                        host.port
                    ),
                    accessory = IosAccessory.Chevron,
                    showSeparator = index != state.hosts.lastIndex,
                    onClick = { onOpen(host) }
                )
            }
        }
    }
}
