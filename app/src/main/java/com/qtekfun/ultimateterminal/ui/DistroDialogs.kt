// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.distro.DistroNames
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily

private val MIN_TOUCH = 48.dp

@Composable
internal fun InstallDialog(
    existingNames: List<String>,
    onInstall: (DistroFamily, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var family by rememberSaveable { mutableStateOf(DistroFamily.DEBIAN) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var user by rememberSaveable { mutableStateOf(NewDistro.DEFAULT_USER) }
    val familyName = stringResource(family.nameRes())
    // Follows the chosen family until the user types a name of their own.
    val shownName = name ?: DistroNames.suggest(familyName, existingNames)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.install_title)) },
        text = {
            Column {
                DistroFamily.entries.forEach { option ->
                    FamilyOption(option, selected = option == family) { family = option }
                }
                OutlinedTextField(
                    value = shownName,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.install_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.install_user_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onInstall(family, shownName, user) }) {
                Text(stringResource(R.string.install_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun FamilyOption(family: DistroFamily, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(family.nameRes()), modifier = Modifier.padding(start = 8.dp))
    }
}

/** A dialog with one text field, used to rename and to name a copy. */
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.install_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
internal fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title, name)) },
        text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.distro_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
