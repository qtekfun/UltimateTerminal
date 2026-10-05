// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.ExportForm
import com.qtekfun.ultimateterminal.domain.backup.ExportFormProblem
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

/** Asks what to export and with which password; the rules live in [ExportForm]. */
@Composable
internal fun ExportSheet(
    distros: List<Distro>,
    onConfirm: (ExportRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by rememberSaveable { mutableStateOf(BackupKind.ALL) }
    var distroId by rememberSaveable { mutableStateOf<Long?>(distros.firstOrNull()?.id) }
    var includeKeys by rememberSaveable { mutableStateOf(true) }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val problem = ExportForm.problem(kind, distroId, includeKeys, password, repeat)
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.backup_dialog_title),
                cancelLabel = stringResource(R.string.backup_cancel),
                confirmLabel = stringResource(R.string.backup_choose_file),
                onCancel = onDismiss,
                onConfirm = {
                    onConfirm(ExportForm.request(kind, distroId, includeKeys, password))
                },
                confirmEnabled = problem == null
            )
            KindSection(kind) { kind = it }
            if (kind == BackupKind.DISTRO) DistroSection(distros, distroId) { distroId = it }
            if (kind != BackupKind.DISTRO) KeysSection(includeKeys) { includeKeys = it }
            PasswordSection(password, repeat, problem, { password = it }, { repeat = it })
        }
    }
}

@Composable
private fun problemText(problem: ExportFormProblem): String = stringResource(
    when (problem) {
        ExportFormProblem.NO_DISTRO -> R.string.backup_no_distros
        ExportFormProblem.PASSWORD_MISMATCH -> R.string.backup_password_mismatch
        ExportFormProblem.KEYS_NEED_PASSWORD -> R.string.backup_password_needed_for_keys
    }
)

private val kinds = listOf(
    BackupKind.ALL to R.string.backup_kind_all,
    BackupKind.CONFIG to R.string.backup_kind_config,
    BackupKind.DISTRO to R.string.backup_kind_distro
)

@Composable
private fun KindSection(kind: BackupKind, onSelect: (BackupKind) -> Unit) {
    IosSection(header = stringResource(R.string.backup_what_header)) {
        kinds.forEachIndexed { index, (option, label) ->
            IosListRow(
                title = stringResource(label),
                accessory = if (kind == option) IosAccessory.Check else IosAccessory.None,
                showSeparator = index != kinds.lastIndex,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun DistroSection(distros: List<Distro>, selected: Long?, onSelect: (Long) -> Unit) {
    IosSection(header = stringResource(R.string.backup_distro_header)) {
        distros.forEachIndexed { index, distro ->
            IosListRow(
                title = distro.name,
                accessory = if (selected == distro.id) IosAccessory.Check else IosAccessory.None,
                showSeparator = index != distros.lastIndex,
                onClick = { onSelect(distro.id) }
            )
        }
    }
}

@Composable
private fun KeysSection(checked: Boolean, onChange: (Boolean) -> Unit) {
    IosSection {
        IosListRow(
            title = stringResource(R.string.backup_include_keys),
            accessory = IosAccessory.Toggle(checked, onChange),
            showSeparator = false
        )
    }
}

@Composable
private fun PasswordSection(
    password: String,
    repeat: String,
    problem: ExportFormProblem?,
    onPassword: (String) -> Unit,
    onRepeat: (String) -> Unit
) {
    IosSection(header = stringResource(R.string.backup_password_header)) {
        IosTextField(
            value = password,
            onValueChange = onPassword,
            label = stringResource(R.string.backup_password),
            secure = true,
            showSeparator = password.isNotEmpty()
        )
        if (password.isNotEmpty()) {
            IosTextField(
                value = repeat,
                onValueChange = onRepeat,
                label = stringResource(R.string.backup_password_repeat),
                secure = true,
                isError = problem == ExportFormProblem.PASSWORD_MISMATCH,
                showSeparator = false
            )
        }
    }
    if (problem != null) {
        IosSection {
            IosListRow(title = problemText(problem), destructive = true, showSeparator = false)
        }
    }
}

/** Asks for the password of an encrypted backup before anything is read from it. */
@Composable
internal fun PasswordSheet(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.backup_password_title),
                cancelLabel = stringResource(R.string.backup_cancel),
                confirmLabel = stringResource(R.string.backup_continue),
                onCancel = onDismiss,
                onConfirm = { onConfirm(password) },
                confirmEnabled = password.isNotEmpty()
            )
            IosSection(footer = stringResource(R.string.backup_password_prompt)) {
                IosTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.backup_password),
                    secure = true,
                    showSeparator = false
                )
            }
        }
    }
}
