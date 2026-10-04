// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.backup.BackupUiState
import com.qtekfun.ultimateterminal.backup.BackupViewModel
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest

private val MIN_TOUCH = 48.dp
private const val BACKUP_MIME = "application/octet-stream"
private const val BACKUP_FILE_NAME = "ultimateterminal-backup.utbackup"

/**
 * Export and restore of backups (SPEC RF-06). The files are chosen with the system picker, so the
 * app needs no storage permission for this.
 */
@Composable
fun BackupCard(viewModel: BackupViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var exporting by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ExportRequest?>(null) }
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri ->
        val request = pending
        pending = null
        if (uri != null && request != null) viewModel.export(uri, request)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::startRestore)
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.backup_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.backup_explanation),
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.distros.isEmpty()) {
                Text(
                    stringResource(R.string.backup_empty_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ActionRow(
                state = state,
                onExport = { exporting = true },
                onRestore = { open.launch(arrayOf("*/*")) }
            )
            BackupStatus(state, viewModel)
        }
    }
    if (exporting) {
        ExportDialog(
            distros = state.distros,
            onConfirm = { request ->
                exporting = false
                pending = request
                create.launch(BACKUP_FILE_NAME)
            },
            onDismiss = { exporting = false }
        )
    }
    state.passwordFor?.let { file ->
        PasswordDialog(
            onConfirm = { password -> viewModel.restoreEncrypted(file, password) },
            onDismiss = viewModel::cancelPassword
        )
    }
}

@Composable
private fun ActionRow(state: BackupUiState, onExport: () -> Unit, onRestore: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onExport,
            enabled = !state.busy,
            modifier = Modifier.heightIn(min = MIN_TOUCH)
        ) { Text(stringResource(R.string.backup_export)) }
        OutlinedButton(
            onClick = onRestore,
            enabled = !state.busy,
            modifier = Modifier.heightIn(min = MIN_TOUCH)
        ) { Text(stringResource(R.string.backup_restore)) }
    }
}

@Composable
private fun BackupStatus(state: BackupUiState, viewModel: BackupViewModel) {
    if (state.busy) {
        state.progress?.let { Text(stringResource(it.phase.labelRes())) }
        val fraction = state.progress?.fraction
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = viewModel::cancel, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
            Text(stringResource(R.string.backup_working_cancel))
        }
    }
    state.message?.let { message ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(backupMessageText(message), modifier = Modifier.weight(1f))
            TextButton(
                onClick = viewModel::dismissMessage,
                modifier = Modifier.heightIn(min = MIN_TOUCH)
            ) {
                Text(stringResource(R.string.msg_dismiss))
            }
        }
    }
}
