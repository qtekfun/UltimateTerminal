// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.distro.DistroMessage
import com.qtekfun.ultimateterminal.distro.DistroUiState
import com.qtekfun.ultimateterminal.distro.DistroViewModel
import com.qtekfun.ultimateterminal.distro.InstallUiState
import com.qtekfun.ultimateterminal.domain.distro.DistroNames
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosBarIconButton
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosProgress
import com.qtekfun.ultimateterminal.ui.ios.IosSection

/** The distribution management screen: list, install, rename, duplicate, delete, default (SPEC RF-04). */
@Composable
fun DistroScreen(onClose: () -> Unit, viewModel: DistroViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DistroDialog?>(null) }
    val canInstall = state.ready && state.installing == null
    val installLabel = stringResource(R.string.distros_install)
    BackHandler(onBack = onClose)
    IosLargeTitleScreen(
        title = stringResource(R.string.distros_title),
        leading = { IosBarButton(stringResource(R.string.distros_close), onClose) },
        trailing = {
            IosBarIconButton(IosGlyph.PLUS, installLabel, {
                if (canInstall) {
                    dialog =
                        DistroDialog.Install
                }
            })
        }
    ) {
        state.installing?.let { install ->
            item { InstallProgress(install, viewModel::cancelInstall) }
        }
        state.message?.let { message -> item { MessageRow(message, viewModel::dismissMessage) } }
        item {
            DistroList(state, canInstall, onInstall = { dialog = DistroDialog.Install }) {
                dialog = DistroDialog.Actions(it)
            }
        }
    }
    DistroDialogHost(
        dialog = dialog,
        distros = state.distros,
        viewModel = viewModel,
        show = { dialog = it },
        // The action sheet closes itself after an action ran, and the action may have opened the
        // next dialog: only close what is still the sheet, reading the state as it is now.
        closeActions = { if (dialog is DistroDialog.Actions) dialog = null }
    )
}

private sealed interface DistroDialog {
    data object Install : DistroDialog

    data class Actions(val distro: Distro) : DistroDialog

    data class Rename(val distro: Distro) : DistroDialog

    data class Duplicate(val distro: Distro) : DistroDialog

    data class Delete(val distro: Distro) : DistroDialog
}

@Composable
private fun DistroDialogHost(
    dialog: DistroDialog?,
    distros: List<Distro>,
    viewModel: DistroViewModel,
    show: (DistroDialog?) -> Unit,
    closeActions: () -> Unit
) {
    val names = distros.map { it.name }
    val dismiss = { show(null) }
    when (dialog) {
        null -> Unit

        DistroDialog.Install -> InstallSheet(
            existingNames = names,
            onInstall = { family, name, user ->
                viewModel.install(family, name, user)
                dismiss()
            },
            onDismiss = dismiss
        )

        is DistroDialog.Actions -> ActionsSheet(dialog.distro, viewModel, closeActions, show)

        is DistroDialog.Rename -> NameSheet(
            title = stringResource(R.string.rename_title),
            initial = dialog.distro.name,
            onConfirm = {
                viewModel.rename(dialog.distro.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )

        is DistroDialog.Duplicate -> NameSheet(
            title = stringResource(R.string.duplicate_title),
            initial = DistroNames.suggestCopy(dialog.distro.name, names),
            onConfirm = {
                viewModel.duplicate(dialog.distro.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )

        is DistroDialog.Delete -> DeleteAlert(
            name = dialog.distro.name,
            onConfirm = {
                viewModel.delete(dialog.distro.id)
                dismiss()
            },
            onDismiss = dismiss
        )
    }
}

/** What can be done with one distribution: the sheet that slides up when its row is tapped. */
@Composable
private fun ActionsSheet(
    distro: Distro,
    viewModel: DistroViewModel,
    dismiss: () -> Unit,
    open: (DistroDialog?) -> Unit
) {
    val ready = distro.state == DistroState.READY
    val busy = distro.state == DistroState.INSTALLING
    val actions = buildList {
        if (ready && !distro.isDefault) {
            add(
                IosAction(stringResource(R.string.distro_set_default)) {
                    viewModel.setDefault(distro.id)
                }
            )
        }
        if (!busy) {
            add(
                IosAction(stringResource(R.string.distro_rename)) {
                    open(DistroDialog.Rename(distro))
                }
            )
        }
        if (ready) {
            add(
                IosAction(stringResource(R.string.distro_duplicate)) {
                    open(DistroDialog.Duplicate(distro))
                }
            )
        }
        if (!busy) {
            add(
                IosAction(stringResource(R.string.distro_delete), IosActionRole.DESTRUCTIVE) {
                    open(DistroDialog.Delete(distro))
                }
            )
        }
    }
    IosActionSheet(
        actions = actions,
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = dismiss,
        title = distro.name
    )
}

@Composable
private fun DeleteAlert(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    IosAlert(
        title = stringResource(R.string.delete_title, name),
        message = stringResource(R.string.delete_body),
        actions = listOf(
            IosAction(stringResource(R.string.dialog_cancel), IosActionRole.CANCEL),
            IosAction(stringResource(R.string.distro_delete), IosActionRole.DESTRUCTIVE, onConfirm)
        ),
        onDismiss = onDismiss
    )
}

@Composable
private fun InstallProgress(install: InstallUiState, onCancel: () -> Unit) {
    IosSection {
        IosListRow(
            title = stringResource(R.string.install_progress_for, install.name),
            subtitle = stringResource(install.progress.phase.labelRes())
        )
        IosProgress(install.progress.fraction, Modifier.padding(horizontal = PROGRESS_INSET))
        IosListRow(
            title = stringResource(R.string.install_cancel),
            destructive = true,
            showSeparator = false,
            onClick = onCancel
        )
    }
}

private val PROGRESS_INSET = 16.dp

@Composable
private fun MessageRow(message: DistroMessage, onDismiss: () -> Unit) {
    IosSection {
        IosListRow(
            title = messageText(message),
            glyph = IosGlyph.INFO,
            accessory = IosAccessory.Value(stringResource(R.string.msg_dismiss)),
            showSeparator = false,
            onClick = onDismiss
        )
    }
}

@Composable
private fun DistroList(
    state: DistroUiState,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onOpen: (Distro) -> Unit
) {
    when {
        !state.ready -> IosSection {
            IosListRow(title = stringResource(R.string.distros_preparing), showSeparator = false)
        }

        state.distros.isEmpty() -> IosSection(footer = stringResource(R.string.distros_empty)) {
            IosListRow(
                title = stringResource(R.string.distros_install),
                glyph = IosGlyph.PLUS,
                accessory = IosAccessory.Chevron,
                enabled = canInstall,
                showSeparator = false,
                onClick = onInstall
            )
        }

        else -> IosSection {
            state.distros.forEachIndexed { index, distro ->
                DistroRow(distro, last = index == state.distros.lastIndex) { onOpen(distro) }
            }
        }
    }
}

@Composable
private fun DistroRow(distro: Distro, last: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val summary = stringResource(
        R.string.distro_summary,
        stringResource(distro.type.nameRes()),
        distro.release,
        formatSize(context, distro.sizeBytes)
    )
    val status = when (distro.state) {
        DistroState.INSTALLING -> stringResource(R.string.distro_state_installing)
        DistroState.FAILED -> stringResource(R.string.distro_state_failed)
        DistroState.READY -> null
    }
    IosListRow(
        title = distro.name,
        subtitle = listOfNotNull(summary, status).joinToString(" · "),
        accessory = if (distro.isDefault && distro.state == DistroState.READY) {
            IosAccessory.Value(stringResource(R.string.distro_default), chevron = true)
        } else {
            IosAccessory.Chevron
        },
        showSeparator = !last,
        onClick = onClick
    )
}
