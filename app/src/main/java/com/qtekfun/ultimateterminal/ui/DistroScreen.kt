// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

private val MIN_TOUCH = 48.dp

/** The distro management screen: list, install, rename, duplicate, delete, default (SPEC RF-04). */
@Composable
fun DistroScreen(onClose: () -> Unit, viewModel: DistroViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DistroDialog?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
            Header(state, onClose, onInstall = { dialog = DistroDialog.Install })
            StorageAccessCard()
            BackupCard()
            state.installing?.let { InstallProgressCard(it, viewModel::cancelInstall) }
            state.message?.let { MessageBar(it, viewModel::dismissMessage) }
            DistroList(
                state = state,
                onSetDefault = viewModel::setDefault,
                onRename = { dialog = DistroDialog.Rename(it) },
                onDuplicate = { dialog = DistroDialog.Duplicate(it) },
                onDelete = { dialog = DistroDialog.Delete(it) }
            )
        }
    }
    DistroDialogHost(dialog, state.distros, viewModel) { dialog = null }
}

private sealed interface DistroDialog {
    data object Install : DistroDialog

    data class Rename(val distro: Distro) : DistroDialog

    data class Duplicate(val distro: Distro) : DistroDialog

    data class Delete(val distro: Distro) : DistroDialog
}

@Composable
private fun DistroDialogHost(
    dialog: DistroDialog?,
    distros: List<Distro>,
    viewModel: DistroViewModel,
    dismiss: () -> Unit
) {
    val names = distros.map { it.name }
    when (dialog) {
        null -> Unit

        DistroDialog.Install -> InstallDialog(
            existingNames = names,
            onInstall = { family, name, user ->
                viewModel.install(family, name, user)
                dismiss()
            },
            onDismiss = dismiss
        )

        is DistroDialog.Rename -> NameDialog(
            title = stringResource(R.string.rename_title),
            initial = dialog.distro.name,
            onConfirm = {
                viewModel.rename(dialog.distro.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )

        is DistroDialog.Duplicate -> NameDialog(
            title = stringResource(R.string.duplicate_title),
            initial = DistroNames.suggestCopy(dialog.distro.name, names),
            onConfirm = {
                viewModel.duplicate(dialog.distro.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )

        is DistroDialog.Delete -> DeleteDialog(
            name = dialog.distro.name,
            onConfirm = {
                viewModel.delete(dialog.distro.id)
                dismiss()
            },
            onDismiss = dismiss
        )
    }
}

@Composable
private fun Header(state: DistroUiState, onClose: () -> Unit, onInstall: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.distros_title), style = MaterialTheme.typography.headlineSmall)
        Row {
            Button(
                onClick = onInstall,
                enabled = state.ready && state.installing == null,
                modifier = Modifier.heightIn(min = MIN_TOUCH)
            ) { Text(stringResource(R.string.distros_install)) }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.distros_close))
            }
        }
    }
}

@Composable
private fun InstallProgressCard(install: InstallUiState, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.install_progress_for, install.name))
            Text(stringResource(install.progress.phase.labelRes()))
            val fraction = install.progress.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.install_cancel))
            }
        }
    }
}

@Composable
private fun MessageBar(message: DistroMessage, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(messageText(message), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.msg_dismiss))
            }
        }
    }
}

@Composable
private fun DistroList(
    state: DistroUiState,
    onSetDefault: (Long) -> Unit,
    onRename: (Distro) -> Unit,
    onDuplicate: (Distro) -> Unit,
    onDelete: (Distro) -> Unit
) {
    when {
        !state.ready -> Text(stringResource(R.string.distros_preparing))

        state.distros.isEmpty() -> Text(
            stringResource(R.string.distros_empty),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.distros, key = { it.id }) { distro ->
                DistroCard(distro, onSetDefault, onRename, onDuplicate, onDelete)
            }
        }
    }
}

@Composable
private fun DistroCard(
    distro: Distro,
    onSetDefault: (Long) -> Unit,
    onRename: (Distro) -> Unit,
    onDuplicate: (Distro) -> Unit,
    onDelete: (Distro) -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(distro.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.distro_summary,
                    stringResource(distro.type.nameRes()),
                    distro.release,
                    formatSize(context, distro.sizeBytes)
                )
            )
            when (distro.state) {
                DistroState.INSTALLING -> Text(stringResource(R.string.distro_state_installing))

                DistroState.FAILED -> Text(stringResource(R.string.distro_state_failed))

                DistroState.READY -> if (distro.isDefault) {
                    Text(stringResource(R.string.distro_default))
                }
            }
            DistroActions(distro, onSetDefault, onRename, onDuplicate, onDelete)
        }
    }
}

@Composable
private fun DistroActions(
    distro: Distro,
    onSetDefault: (Long) -> Unit,
    onRename: (Distro) -> Unit,
    onDuplicate: (Distro) -> Unit,
    onDelete: (Distro) -> Unit
) {
    val ready = distro.state == DistroState.READY
    val busy = distro.state == DistroState.INSTALLING
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (ready && !distro.isDefault) {
            ActionButton(R.string.distro_set_default) { onSetDefault(distro.id) }
        }
        ActionButton(R.string.distro_rename, enabled = !busy) { onRename(distro) }
        if (ready) ActionButton(R.string.distro_duplicate) { onDuplicate(distro) }
        ActionButton(R.string.distro_delete, enabled = !busy) { onDelete(distro) }
    }
}

@Composable
private fun ActionButton(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = MIN_TOUCH)
    ) {
        Text(stringResource(label))
    }
}
