// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.ssh.SshKeysUiState
import com.qtekfun.ultimateterminal.ssh.SshKeysViewModel

private val MIN_TOUCH = 48.dp

private sealed interface KeyDialog {
    data object Generate : KeyDialog

    data object Import : KeyDialog

    data class Delete(val key: SshKeyInfo) : KeyDialog

    /** The warning shown before the private key is written anywhere. */
    data class ExportWarning(val key: SshKeyInfo) : KeyDialog
}

/** Key management: generate, import, copy the public key, save the private key, delete. */
@Composable
internal fun SshKeysScreen(onBack: () -> Unit, viewModel: SshKeysViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<KeyDialog?>(null) }
    var exporting by remember { mutableStateOf<SshKeyInfo?>(null) }
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val key = exporting
        if (uri != null && key != null) {
            viewModel.savePrivateKey(key.alias, context.contentResolver, uri)
        }
        exporting = null
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.safeDrawingPadding().padding(16.dp)) {
            KeysHeader(
                onGenerate = { dialog = KeyDialog.Generate },
                onImport = { dialog = KeyDialog.Import },
                onBack = onBack
            )
            if (state.busy) BusyBar()
            state.message?.let { SshMessageBar(it, viewModel::dismissMessage) }
            KeyList(
                keys = state.keys,
                onCopy = { key -> viewModel.publicKey(key.alias) { copyToClipboard(context, it) } },
                onExport = { dialog = KeyDialog.ExportWarning(it) },
                onDelete = { dialog = KeyDialog.Delete(it) }
            )
        }
    }
    KeyDialogHost(
        dialog = dialog,
        state = state,
        viewModel = viewModel,
        onChooseExportFile = { key ->
            exporting = key
            saveFile.launch(exportFileName(key))
        },
        dismiss = { dialog = null }
    )
}

@Composable
private fun KeyDialogHost(
    dialog: KeyDialog?,
    state: SshKeysUiState,
    viewModel: SshKeysViewModel,
    onChooseExportFile: (SshKeyInfo) -> Unit,
    dismiss: () -> Unit
) {
    when (dialog) {
        null -> Unit

        KeyDialog.Generate -> GenerateKeyDialog(
            types = state.keyTypes,
            onGenerate = { name, type ->
                viewModel.generateKey(name, type)
                dismiss()
            },
            onDismiss = dismiss
        )

        KeyDialog.Import -> ImportKeyDialog(
            viewModel = viewModel,
            onImport = { name, text ->
                viewModel.importKey(name, text)
                dismiss()
            },
            onDismiss = dismiss
        )

        is KeyDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.ssh_key_delete_title, dialog.key.name),
            body = stringResource(R.string.ssh_key_delete_body),
            confirm = stringResource(R.string.ssh_key_delete),
            onConfirm = {
                viewModel.removeKey(dialog.key.alias)
                dismiss()
            },
            onDismiss = dismiss
        )

        is KeyDialog.ExportWarning -> ConfirmDialog(
            title = stringResource(R.string.ssh_export_title),
            body = stringResource(R.string.ssh_export_body),
            confirm = stringResource(R.string.ssh_export_confirm),
            onConfirm = {
                onChooseExportFile(dialog.key)
                dismiss()
            },
            onDismiss = dismiss
        )
    }
}

@Composable
private fun KeysHeader(onGenerate: () -> Unit, onImport: () -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ssh_keys_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGenerate, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_key_generate))
            }
            TextButton(onClick = onImport, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_key_import))
            }
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_close))
            }
        }
    }
}

@Composable
private fun KeyList(
    keys: List<SshKeyInfo>,
    onCopy: (SshKeyInfo) -> Unit,
    onExport: (SshKeyInfo) -> Unit,
    onDelete: (SshKeyInfo) -> Unit
) {
    if (keys.isEmpty()) {
        Text(
            stringResource(R.string.ssh_keys_empty),
            modifier = Modifier.padding(vertical = 24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(keys, key = { it.alias }) { key -> KeyCard(key, onCopy, onExport, onDelete) }
        }
    }
}

@Composable
private fun KeyCard(
    key: SshKeyInfo,
    onCopy: (SshKeyInfo) -> Unit,
    onExport: (SshKeyInfo) -> Unit,
    onDelete: (SshKeyInfo) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(key.name, style = MaterialTheme.typography.titleMedium)
            Text(key.type.sshName, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.ssh_key_fingerprint, key.fingerprint),
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    onCopy(key)
                }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                    Text(stringResource(R.string.ssh_key_copy_public))
                }
                TextButton(onClick = {
                    onExport(key)
                }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                    Text(stringResource(R.string.ssh_key_export_private))
                }
                TextButton(onClick = {
                    onDelete(key)
                }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                    Text(stringResource(R.string.ssh_delete))
                }
            }
        }
    }
}

private fun exportFileName(key: SshKeyInfo): String =
    "id_" + key.type.sshName.removePrefix("ssh-") + "_" + key.name.filter { it.isLetterOrDigit() }

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("ssh public key", text))
}
