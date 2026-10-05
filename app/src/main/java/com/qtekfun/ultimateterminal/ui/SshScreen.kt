// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.ssh.SshUiState
import com.qtekfun.ultimateterminal.ssh.SshViewModel

private val MIN_TOUCH = 48.dp

/** The SSH hosts screen (SPEC RF-09): saved servers, one tap to connect, and the key manager. */
@Composable
fun SshScreen(onClose: () -> Unit, viewModel: SshViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showKeys by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HostDialog?>(null) }
    if (showKeys) {
        SshKeysScreen(onBack = { showKeys = false })
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
                HostsHeader(
                    onAdd = { dialog = HostDialog.Edit(null) },
                    onKeys = { showKeys = true },
                    onClose = onClose
                )
                if (state.busy) BusyBar()
                state.message?.let { SshMessageBar(it, viewModel::dismissMessage) }
                HostList(
                    state = state,
                    onConnect = { viewModel.connect(it.id, onOpened = onClose) },
                    onEdit = { dialog = HostDialog.Edit(it) },
                    onDelete = { dialog = HostDialog.Delete(it) }
                )
            }
        }
        HostDialogHost(dialog, state, viewModel) { dialog = null }
    }
}

internal sealed interface HostDialog {
    /** [host] is null when adding a new one. */
    data class Edit(val host: SshHost?) : HostDialog

    data class Delete(val host: SshHost) : HostDialog
}

@Composable
private fun HostDialogHost(
    dialog: HostDialog?,
    state: SshUiState,
    viewModel: SshViewModel,
    dismiss: () -> Unit
) {
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

@Composable
private fun HostsHeader(onAdd: () -> Unit, onKeys: () -> Unit, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ssh_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_add_host))
            }
            TextButton(onClick = onKeys, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_keys_open))
            }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_close))
            }
        }
    }
}

@Composable
internal fun BusyBar() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.ssh_busy), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HostList(
    state: SshUiState,
    onConnect: (SshHost) -> Unit,
    onEdit: (SshHost) -> Unit,
    onDelete: (SshHost) -> Unit
) {
    if (state.hosts.isEmpty()) {
        Text(
            stringResource(R.string.ssh_no_hosts),
            modifier = Modifier.padding(vertical = 24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.hosts, key = { it.id }) { host ->
                HostCard(host, !state.busy, onConnect, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun HostCard(
    host: SshHost,
    enabled: Boolean,
    onConnect: (SshHost) -> Unit,
    onEdit: (SshHost) -> Unit,
    onDelete: (SshHost) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(host.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.ssh_host_summary, host.user, host.host, host.port),
                style = MaterialTheme.typography.bodyMedium
            )
            val connectLabel = stringResource(R.string.ssh_connect_to, host.name)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onConnect(host) },
                    enabled = enabled,
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH)
                        .semantics { contentDescription = connectLabel }
                ) { Text(stringResource(R.string.ssh_connect)) }
                TextButton(onClick = {
                    onEdit(host)
                }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                    Text(stringResource(R.string.ssh_edit))
                }
                TextButton(onClick = {
                    onDelete(host)
                }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                    Text(stringResource(R.string.ssh_delete))
                }
            }
        }
    }
}
