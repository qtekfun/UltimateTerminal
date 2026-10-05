// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.backup.BackupUiState
import com.qtekfun.ultimateterminal.backup.BackupViewModel
import com.qtekfun.ultimateterminal.domain.backup.BackupFileName
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.ui.ExportSheet
import com.qtekfun.ultimateterminal.ui.PasswordSheet
import com.qtekfun.ultimateterminal.ui.backupMessageText
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosProgress
import com.qtekfun.ultimateterminal.ui.labelRes

private const val BACKUP_MIME = "application/octet-stream"

/**
 * Export and restore of backups (SPEC RF-06). The files are chosen with the system picker, so the
 * app needs no storage permission for this.
 */
@Composable
internal fun BackupPage(nav: PageNav, viewModel: BackupViewModel = viewModel()) {
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
    val explanation = stringResource(R.string.backup_explanation) + " " +
        stringResource(R.string.backup_where_it_goes) + hint(state)
    SettingsPage(stringResource(R.string.settings_section_backup), nav.backLabel, nav.back) {
        section(footer = explanation) {
            IosListRow(
                title = stringResource(R.string.settings_backup_export),
                glyph = IosGlyph.DOWNLOAD,
                enabled = !state.busy,
                onClick = { exporting = true }
            )
            IosListRow(
                title = stringResource(R.string.settings_backup_restore),
                glyph = IosGlyph.FOLDER,
                enabled = !state.busy,
                showSeparator = false,
                onClick = { open.launch(arrayOf("*/*")) }
            )
        }
        if (state.busy) section { Working(state, viewModel::cancel) }
        state.message?.let { message ->
            section {
                IosListRow(
                    title = backupMessageText(message),
                    glyph = IosGlyph.INFO,
                    accessory = IosAccessory.Value(stringResource(R.string.msg_dismiss)),
                    showSeparator = false,
                    onClick = viewModel::dismissMessage
                )
            }
        }
    }
    BackupSheets(
        state = state,
        exporting = exporting,
        onExportConfirm = { request ->
            exporting = false
            pending = request
            val distroName = state.distros.firstOrNull { it.id == request.distroId }?.name
            create.launch(
                BackupFileName.suggest(request.kind, distroName, LocalDate.now())
            )
        },
        onExportDismiss = { exporting = false },
        viewModel = viewModel
    )
}

/** The export form and the password prompt of an encrypted backup, when they are due. */
@Composable
private fun BackupSheets(
    state: BackupUiState,
    exporting: Boolean,
    onExportConfirm: (ExportRequest) -> Unit,
    onExportDismiss: () -> Unit,
    viewModel: BackupViewModel
) {
    if (exporting) {
        ExportSheet(
            distros = state.distros,
            onConfirm = onExportConfirm,
            onDismiss = onExportDismiss
        )
    }
    state.passwordFor?.let { file ->
        PasswordSheet(
            onConfirm = { password -> viewModel.restoreEncrypted(file, password) },
            onDismiss = viewModel::cancelPassword
        )
    }
}

@Composable
private fun hint(state: BackupUiState): String =
    if (state.distros.isEmpty()) " " + stringResource(R.string.backup_empty_hint) else ""

/** The progress of what is running, with a way to stop it. */
@Composable
internal fun Working(state: BackupUiState, onCancel: () -> Unit) {
    IosListRow(
        title = state.progress?.let { stringResource(it.phase.labelRes()) }.orEmpty(),
        showSeparator = true
    )
    IosProgress(state.progress?.fraction, Modifier.padding(horizontal = 16.dp))
    IosListRow(
        title = stringResource(R.string.backup_working_cancel),
        destructive = true,
        showSeparator = false,
        onClick = onCancel
    )
}
