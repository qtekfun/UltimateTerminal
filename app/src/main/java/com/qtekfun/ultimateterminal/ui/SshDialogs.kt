// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.ssh.SshKeysViewModel
import com.qtekfun.ultimateterminal.ssh.SshMessage
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosAlert
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosProgress
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextArea
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

private val PROGRESS_INSET = 16.dp

/** The result of the last action, as a row that dismisses itself when tapped. */
@Composable
internal fun SshMessageBar(message: SshMessage, onDismiss: () -> Unit) {
    IosSection {
        IosListRow(
            title = message.text(),
            glyph = IosGlyph.INFO,
            accessory = IosAccessory.Value(stringResource(R.string.msg_dismiss)),
            showSeparator = false,
            onClick = onDismiss
        )
    }
}

/** Shown while a key is being generated, imported or saved. */
@Composable
internal fun BusyBar() {
    IosSection {
        IosListRow(title = stringResource(R.string.ssh_busy), showSeparator = false)
        IosProgress(null, Modifier.padding(horizontal = PROGRESS_INSET, vertical = 8.dp))
    }
}

/** An alert with a cancel button and one confirming action, in red when [destructive]. */
@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true
) {
    val role = if (destructive) IosActionRole.DESTRUCTIVE else IosActionRole.DEFAULT
    IosAlert(
        title = title,
        message = body,
        actions = listOf(
            IosAction(stringResource(R.string.dialog_cancel), IosActionRole.CANCEL),
            IosAction(confirm, role, onConfirm)
        ),
        onDismiss = onDismiss
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
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.ssh_save),
                onCancel = onDismiss,
                onConfirm = { onSave(draft.toHost(host?.id ?: 0L)) }
            )
            HostFields(draft, keys, distros) { draft = it }
        }
    }
}

@Composable
private fun HostFields(
    draft: HostDraft,
    keys: List<SshKeyInfo>,
    distros: List<Distro>,
    onChange: (HostDraft) -> Unit
) {
    IosSection {
        IosTextField(
            draft.name,
            { onChange(draft.copy(name = it)) },
            stringResource(R.string.ssh_field_name)
        )
        IosTextField(
            draft.address,
            { onChange(draft.copy(address = it)) },
            stringResource(R.string.ssh_field_host),
            keyboardType = KeyboardType.Uri
        )
        IosTextField(
            draft.port,
            { onChange(draft.copy(port = it.filter(Char::isDigit))) },
            stringResource(R.string.ssh_field_port),
            keyboardType = KeyboardType.Number
        )
        IosTextField(
            draft.user,
            { onChange(draft.copy(user = it)) },
            stringResource(R.string.ssh_field_user),
            showSeparator = false
        )
    }
    val noKey = listOf<Pair<String?, String>>(null to stringResource(R.string.ssh_key_none))
    ChoiceSection(
        header = stringResource(R.string.ssh_field_key),
        options = noKey + keys.map { it.alias to it.name },
        selected = draft.keyAlias,
        onSelect = { onChange(draft.copy(keyAlias = it)) }
    )
    val defaultDistro =
        listOf<Pair<Long?, String>>(null to stringResource(R.string.ssh_distro_default))
    ChoiceSection(
        header = stringResource(R.string.ssh_field_distro),
        options = defaultDistro + distros.map { it.id to it.name },
        selected = draft.distroId,
        onSelect = { onChange(draft.copy(distroId = it)) }
    )
}

/** A short list of choices with a check on the chosen one; the options are few (keys, distros). */
@Composable
private fun <T> ChoiceSection(
    header: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    IosSection(header = header) {
        options.forEachIndexed { index, (value, text) ->
            IosListRow(
                title = text,
                accessory = if (value == selected) IosAccessory.Check else IosAccessory.None,
                showSeparator = index != options.lastIndex,
                onClick = { onSelect(value) }
            )
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
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.ssh_generate_title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.ssh_key_generate),
                onCancel = onDismiss,
                onConfirm = { onGenerate(name, type) }
            )
            IosSection {
                IosTextField(
                    name,
                    { name = it },
                    stringResource(R.string.ssh_field_name),
                    showSeparator = false
                )
            }
            ChoiceSection(
                header = stringResource(R.string.ssh_field_key),
                options = types.map { it to stringResource(typeLabel(it)) },
                selected = type,
                onSelect = { type = it }
            )
        }
    }
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
    // The last name we filled in, so a second pick replaces it but never what the user typed.
    var suggested by rememberSaveable { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.readKeyFile(resolver, uri) { content, suggestion ->
                unreadable = content == null
                content?.let {
                    text = it
                    if (suggestion.isNotEmpty() && (name.isBlank() || name == suggested)) {
                        name = suggestion
                        suggested = suggestion
                    }
                }
            }
        }
    }
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.ssh_import_title),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.ssh_key_import),
                onCancel = onDismiss,
                onConfirm = { onImport(name, text) }
            )
            val footer = stringResource(
                if (unreadable) R.string.ssh_import_unreadable else R.string.ssh_import_hint
            )
            IosSection(footer = footer) {
                IosTextField(name, { name = it }, stringResource(R.string.ssh_field_name))
                IosTextArea(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.ssh_import_field),
                    isError = unreadable,
                    showSeparator = false
                )
            }
            IosSection {
                IosListRow(
                    title = stringResource(R.string.ssh_import_choose),
                    glyph = IosGlyph.FOLDER,
                    accessory = IosAccessory.Chevron,
                    showSeparator = false,
                    onClick = { pick.launch(arrayOf("*/*")) }
                )
            }
        }
    }
}
