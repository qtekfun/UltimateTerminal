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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.ssh.SshKeysViewModel
import com.qtekfun.ultimateterminal.ssh.SshMessage

private val MIN_TOUCH = 48.dp

@Composable
internal fun SshMessageBar(message: SshMessage, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(message.text(), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.msg_dismiss))
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/** The form of the host dialog; the port stays text while typing and is checked on save. */
private data class HostDraft(
    val name: String,
    val address: String,
    val port: String,
    val user: String,
    val keyAlias: String?,
    val distroId: Long?
) {
    fun toHost(id: Long) = SshHost(
        id = id,
        name = name,
        host = address,
        port = port.toIntOrNull() ?: 0,
        user = user,
        keyAlias = keyAlias,
        distroId = distroId
    )

    companion object {
        fun of(host: SshHost?) = HostDraft(
            name = host?.name.orEmpty(),
            address = host?.host.orEmpty(),
            port = (host?.port ?: SshHost.DEFAULT_PORT).toString(),
            user = host?.user.orEmpty(),
            keyAlias = host?.keyAlias,
            distroId = host?.distroId
        )

        /** Survives rotation and the process being recreated while the dialog is open. */
        val Saver = listSaver<HostDraft, Any?>(
            save = { listOf(it.name, it.address, it.port, it.user, it.keyAlias, it.distroId) },
            restore = {
                HostDraft(
                    it[0] as String,
                    it[1] as String,
                    it[2] as String,
                    it[3] as String,
                    it[4] as String?,
                    it[5] as Long?
                )
            }
        )
    }
}

/** Adds a host, or edits [host]. */
@Composable
internal fun HostEditDialog(
    host: SshHost?,
    keys: List<SshKeyInfo>,
    distros: List<Distro>,
    onSave: (SshHost) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(stateSaver = HostDraft.Saver) {
        mutableStateOf(HostDraft.of(host))
    }
    val title = if (host == null) R.string.ssh_host_add_title else R.string.ssh_host_edit_title
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { HostFields(draft, keys, distros) { draft = it } },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft.toHost(host?.id ?: 0L)) },
                modifier = Modifier.heightIn(min = MIN_TOUCH)
            ) { Text(stringResource(R.string.ssh_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun HostFields(
    draft: HostDraft,
    keys: List<SshKeyInfo>,
    distros: List<Distro>,
    onChange: (HostDraft) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Field(R.string.ssh_field_name, draft.name) { onChange(draft.copy(name = it)) }
        Field(R.string.ssh_field_host, draft.address) { onChange(draft.copy(address = it)) }
        Field(R.string.ssh_field_port, draft.port, KeyboardType.Number) {
            onChange(draft.copy(port = it.filter(Char::isDigit)))
        }
        Field(R.string.ssh_field_user, draft.user) { onChange(draft.copy(user = it)) }
        val noKey = listOf<Pair<String?, String>>(null to stringResource(R.string.ssh_key_none))
        Choice(R.string.ssh_field_key, noKey + keys.map { it.alias to it.name }, draft.keyAlias) {
            onChange(draft.copy(keyAlias = it))
        }
        val defaultDistro =
            listOf<Pair<Long?, String>>(null to stringResource(R.string.ssh_distro_default))
        val distroChoices = defaultDistro + distros.map { it.id to it.name }
        Choice(R.string.ssh_field_distro, distroChoices, draft.distroId) {
            onChange(draft.copy(distroId = it))
        }
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A short list of radio choices; the options are few (keys, distros), so no menu is needed. */
@Composable
private fun <T> Choice(
    label: Int,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.selectableGroup()) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        options.forEach { (value, text) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH)
                    .selectable(selected = value == selected, role = Role.RadioButton) {
                        onSelect(value)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Text(text, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
internal fun GenerateKeyDialog(
    types: List<SshKeyType>,
    onGenerate: (String, SshKeyType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(types.firstOrNull() ?: SshKeyType.RSA) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ssh_generate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(R.string.ssh_field_name, name) { name = it }
                Choice(
                    label = R.string.ssh_field_key,
                    options = types.map { it to stringResource(typeLabel(it)) },
                    selected = type,
                    onSelect = { type = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onGenerate(name, type)
            }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_key_generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

private fun typeLabel(type: SshKeyType): Int = when (type) {
    SshKeyType.ED25519 -> R.string.ssh_key_type_ed25519
    SshKeyType.RSA -> R.string.ssh_key_type_rsa
}

@Composable
internal fun ImportKeyDialog(
    viewModel: SshKeysViewModel,
    onImport: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val resolver = LocalContext.current.contentResolver
    var name by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var unreadable by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.readKeyFile(resolver, uri) { content ->
                unreadable = content == null
                content?.let { text = it }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ssh_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.ssh_import_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Field(R.string.ssh_field_name, name) { name = it }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.ssh_import_field)) },
                    minLines = 3,
                    maxLines = 6,
                    isError = unreadable,
                    supportingText = if (unreadable) {
                        { Text(stringResource(R.string.ssh_import_unreadable)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { pick.launch(arrayOf("*/*")) },
                    modifier = Modifier.heightIn(min = MIN_TOUCH)
                ) { Text(stringResource(R.string.ssh_import_choose)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onImport(name, text)
            }, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.ssh_key_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
