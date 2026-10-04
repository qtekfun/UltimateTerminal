// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.ExportForm
import com.qtekfun.ultimateterminal.domain.backup.ExportFormProblem
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.model.Distro

private val MIN_TOUCH = 48.dp

/** Asks what to export and with which password; the rules live in [ExportForm]. */
@Composable
internal fun ExportDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_dialog_title)) },
        text = {
            Column {
                KindChoice(kind) { kind = it }
                if (kind == BackupKind.DISTRO) DistroChoice(distros, distroId) { distroId = it }
                if (kind != BackupKind.DISTRO) KeysChoice(includeKeys) { includeKeys = it }
                PasswordFields(password, repeat, { password = it }, { repeat = it })
                problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ExportForm.request(kind, distroId, includeKeys, password)) },
                enabled = problem == null,
                modifier = Modifier.heightIn(min = MIN_TOUCH)
            ) { Text(stringResource(R.string.backup_choose_file)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.backup_cancel))
            }
        }
    )
}

@Composable
private fun problemText(problem: ExportFormProblem): String = stringResource(
    when (problem) {
        ExportFormProblem.NO_DISTRO -> R.string.backup_no_distros
        ExportFormProblem.PASSWORD_MISMATCH -> R.string.backup_password_mismatch
        ExportFormProblem.KEYS_NEED_PASSWORD -> R.string.backup_password_needed_for_keys
    }
)

@Composable
private fun KindChoice(kind: BackupKind, onSelect: (BackupKind) -> Unit) {
    val labels = mapOf(
        BackupKind.ALL to R.string.backup_kind_all,
        BackupKind.CONFIG to R.string.backup_kind_config,
        BackupKind.DISTRO to R.string.backup_kind_distro
    )
    Column {
        for ((option, label) in labels) {
            ChoiceRow(selected = kind == option, text = stringResource(label)) { onSelect(option) }
        }
    }
}

@Composable
private fun DistroChoice(distros: List<Distro>, selected: Long?, onSelect: (Long) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        for (distro in distros) {
            ChoiceRow(selected = selected == distro.id, text = distro.name) { onSelect(distro.id) }
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, text: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun KeysChoice(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH)
            .selectable(selected = checked, role = Role.Checkbox, onClick = { onChange(!checked) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            stringResource(R.string.backup_include_keys),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun PasswordFields(
    password: String,
    repeat: String,
    onPassword: (String) -> Unit,
    onRepeat: (String) -> Unit
) {
    val options = KeyboardOptions(keyboardType = KeyboardType.Password)
    OutlinedTextField(
        value = password,
        onValueChange = onPassword,
        label = { Text(stringResource(R.string.backup_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = options,
        modifier = Modifier.fillMaxWidth()
    )
    if (password.isNotEmpty()) {
        OutlinedTextField(
            value = repeat,
            onValueChange = onRepeat,
            label = { Text(stringResource(R.string.backup_password_repeat)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = options,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Asks for the password of an encrypted backup before anything is read from it. */
@Composable
internal fun PasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_password_prompt))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty(),
                modifier = Modifier.heightIn(min = MIN_TOUCH)
            ) { Text(stringResource(R.string.backup_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.backup_cancel))
            }
        }
    )
}
