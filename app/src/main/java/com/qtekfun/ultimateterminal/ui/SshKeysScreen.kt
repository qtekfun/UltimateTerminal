// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.ssh.SshKeysUiState
import com.qtekfun.ultimateterminal.ssh.SshKeysViewModel
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosAction
import com.qtekfun.ultimateterminal.ui.ios.IosActionRole
import com.qtekfun.ultimateterminal.ui.ios.IosActionSheet
import com.qtekfun.ultimateterminal.ui.ios.IosBarButton
import com.qtekfun.ultimateterminal.ui.ios.IosBarIconButton
import com.qtekfun.ultimateterminal.ui.ios.IosGlyph
import com.qtekfun.ultimateterminal.ui.ios.IosLargeTitleScreen
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection

private sealed interface KeyDialog {
    data object Generate : KeyDialog

    data object Import : KeyDialog

    /** Generate or import: the sheet the "+" button opens. */
    data object Add : KeyDialog

    data class Actions(val key: SshKeyInfo) : KeyDialog

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
    BackHandler(onBack = onBack)
    val addLabel = stringResource(R.string.ssh_key_add)
    IosLargeTitleScreen(
        title = stringResource(R.string.ssh_keys_title),
        leading = { IosBarButton(stringResource(R.string.ssh_close), onBack) },
        trailing = { IosBarIconButton(IosGlyph.PLUS, addLabel, { dialog = KeyDialog.Add }) }
    ) {
        if (state.busy) item { BusyBar() }
        state.message?.let { message ->
            item { SshMessageBar(message, viewModel::dismissMessage) }
        }
        item {
            KeyList(
                state.keys,
                onAdd = { dialog = KeyDialog.Add },
                onOpen = { dialog = KeyDialog.Actions(it) }
            )
        }
    }
    KeyDialogHost(
        dialog = dialog,
        state = state,
        viewModel = viewModel,
        targets = KeyTargets(
            show = { dialog = it },
            // The action sheet closes itself after an action ran, and the action may have opened
            // the next dialog: only close what is still a sheet, reading the state as it is now.
            closeSheet = {
                if (dialog is KeyDialog.Actions ||
                    dialog == KeyDialog.Add
                ) {
                    dialog = null
                }
            },
            copy = { key -> viewModel.publicKey(key.alias) { copyToClipboard(context, it) } },
            chooseExportFile = { key ->
                exporting = key
                saveFile.launch(exportFileName(key))
            }
        )
    )
}

/** What the dialogs of the key manager can ask the screen to do. */
private class KeyTargets(
    val show: (KeyDialog?) -> Unit,
    val closeSheet: () -> Unit,
    val copy: (SshKeyInfo) -> Unit,
    val chooseExportFile: (SshKeyInfo) -> Unit
)

@Composable
private fun KeyDialogHost(
    dialog: KeyDialog?,
    state: SshKeysUiState,
    viewModel: SshKeysViewModel,
    targets: KeyTargets
) {
    val dismiss = { targets.show(null) }
    when (dialog) {
        null -> Unit

        KeyDialog.Add -> AddActions(targets)

        is KeyDialog.Actions -> KeyActions(dialog.key, targets)

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
                targets.chooseExportFile(dialog.key)
                dismiss()
            },
            onDismiss = dismiss,
            destructive = false
        )
    }
}

/** The sheet of the "+" button: a new key, or one brought from elsewhere. */
@Composable
private fun AddActions(targets: KeyTargets) {
    IosActionSheet(
        actions = listOf(
            IosAction(stringResource(R.string.ssh_key_generate)) {
                targets.show(KeyDialog.Generate)
            },
            IosAction(stringResource(R.string.ssh_key_import)) { targets.show(KeyDialog.Import) }
        ),
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = targets.closeSheet,
        title = stringResource(R.string.ssh_key_add)
    )
}

/** What can be done with one key: the sheet that slides up when its row is tapped. */
@Composable
private fun KeyActions(key: SshKeyInfo, targets: KeyTargets) {
    IosActionSheet(
        actions = listOf(
            IosAction(stringResource(R.string.ssh_key_copy_public)) { targets.copy(key) },
            IosAction(stringResource(R.string.ssh_key_export_private)) {
                targets.show(KeyDialog.ExportWarning(key))
            },
            IosAction(stringResource(R.string.ssh_delete), IosActionRole.DESTRUCTIVE) {
                targets.show(KeyDialog.Delete(key))
            }
        ),
        cancelLabel = stringResource(R.string.dialog_cancel),
        onDismiss = targets.closeSheet,
        title = key.name
    )
}

@Composable
private fun KeyList(keys: List<SshKeyInfo>, onAdd: () -> Unit, onOpen: (SshKeyInfo) -> Unit) {
    if (keys.isEmpty()) {
        IosSection(footer = stringResource(R.string.ssh_keys_empty)) {
            IosListRow(
                title = stringResource(R.string.ssh_key_add),
                glyph = IosGlyph.PLUS,
                accessory = IosAccessory.Chevron,
                showSeparator = false,
                onClick = onAdd
            )
        }
    } else {
        IosSection {
            keys.forEachIndexed { index, key ->
                IosListRow(
                    title = key.name,
                    subtitle = key.type.sshName + "\n" +
                        stringResource(R.string.ssh_key_fingerprint, key.fingerprint),
                    glyph = IosGlyph.KEY,
                    accessory = IosAccessory.Chevron,
                    showSeparator = index != keys.lastIndex,
                    onClick = { onOpen(key) }
                )
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
