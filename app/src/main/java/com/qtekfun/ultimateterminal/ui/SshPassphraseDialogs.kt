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
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.SheetDetent
import com.qtekfun.ultimateterminal.domain.ssh.Passphrases
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.ssh.UnlockRequest
import com.qtekfun.ultimateterminal.ui.ios.IosAccessory
import com.qtekfun.ultimateterminal.ui.ios.IosBottomSheet
import com.qtekfun.ultimateterminal.ui.ios.IosListRow
import com.qtekfun.ultimateterminal.ui.ios.IosSection
import com.qtekfun.ultimateterminal.ui.ios.IosSheetHeader
import com.qtekfun.ultimateterminal.ui.ios.IosTextField

// Passphrases are kept in plain `remember`, never `rememberSaveable`: a saved instance state is
// written out of the process, and a passphrase must not be. Turning the screen clears the fields.

/** One secure field in a section of its own, for a passphrase that already exists. */
@Composable
internal fun PassphraseEntrySection(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    footer: String? = null,
    isError: Boolean = false
) {
    IosSection(footer = footer) {
        IosTextField(
            value,
            onChange,
            label,
            secure = true,
            isError = isError,
            showSeparator = false
        )
    }
}

/** The new-passphrase pair of a form: two secure fields and what is wrong with them, if anything. */
@Composable
internal fun NewPassphraseSection(
    passphrase: String,
    confirmation: String,
    onPassphrase: (String) -> Unit,
    onConfirmation: (String) -> Unit,
    hint: String,
    passphraseLabel: String = stringResource(R.string.ssh_field_passphrase_new)
) {
    val check = Passphrases.check(passphrase, confirmation)
    val footer = when (check) {
        Passphrases.Check.OK -> hint

        Passphrases.Check.TOO_SHORT ->
            stringResource(R.string.ssh_err_passphrase_short, Passphrases.MIN_LENGTH)

        Passphrases.Check.MISMATCH -> stringResource(R.string.ssh_err_passphrase_mismatch)
    }
    IosSection(footer = footer) {
        IosTextField(
            passphrase,
            onPassphrase,
            passphraseLabel,
            secure = true,
            isError = check == Passphrases.Check.TOO_SHORT
        )
        IosTextField(
            confirmation,
            onConfirmation,
            stringResource(R.string.ssh_field_passphrase_confirm),
            secure = true,
            isError = check == Passphrases.Check.MISMATCH,
            showSeparator = false
        )
    }
}

/** The passphrase a connection waits for, with the choice to keep the key unlocked for a while. */
@Composable
internal fun UnlockKeyDialog(
    request: UnlockRequest,
    onUnlock: (passphrase: CharArray, remember: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var keep by rememberSaveable { mutableStateOf(false) }
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(R.string.ssh_unlock_title, request.keyName),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(R.string.ssh_unlock_confirm),
                onCancel = onDismiss,
                onConfirm = { onUnlock(passphrase.toCharArray(), keep) },
                confirmEnabled = passphrase.isNotEmpty()
            )
            val footer = stringResource(
                if (request.wrong) R.string.ssh_err_wrong_passphrase else R.string.ssh_unlock_hint
            )
            PassphraseEntrySection(
                value = passphrase,
                onChange = { passphrase = it },
                label = stringResource(R.string.ssh_field_passphrase),
                footer = footer,
                isError = request.wrong
            )
            IosSection(footer = stringResource(R.string.ssh_unlock_keep_hint)) {
                IosListRow(
                    title = stringResource(R.string.ssh_unlock_keep),
                    accessory = IosAccessory.Toggle(keep) { keep = it },
                    showSeparator = false
                )
            }
        }
    }
}

private fun passphraseDialogTitle(protected: Boolean, removing: Boolean): Int = when {
    removing -> R.string.ssh_passphrase_remove_title
    protected -> R.string.ssh_passphrase_change_title
    else -> R.string.ssh_passphrase_set_title
}

/** Set, change or remove the passphrase of a stored key, whichever fits what the key has now. */
@Composable
internal fun ChangePassphraseDialog(
    key: SshKeyInfo,
    removing: Boolean,
    onApply: (current: CharArray, replacement: CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val protected = key.passphraseProtected
    val check = if (removing) Passphrases.Check.OK else Passphrases.check(replacement, confirmation)
    // Setting or changing needs a new passphrase (an empty one would be "remove"); removing and
    // changing both need the current one.
    val complete = (!protected || current.isNotEmpty()) && (removing || replacement.isNotEmpty())
    IosBottomSheet(onDismiss = onDismiss, detents = listOf(SheetDetent.LARGE)) {
        Column {
            IosSheetHeader(
                title = stringResource(passphraseDialogTitle(protected, removing)),
                cancelLabel = stringResource(R.string.dialog_cancel),
                confirmLabel = stringResource(
                    if (removing) R.string.ssh_passphrase_remove else R.string.ssh_passphrase_save
                ),
                onCancel = onDismiss,
                onConfirm = {
                    onApply(
                        current.toCharArray(),
                        if (removing) CharArray(0) else replacement.toCharArray()
                    )
                },
                confirmEnabled = complete && check == Passphrases.Check.OK
            )
            if (protected) {
                IosSection(
                    footer = if (removing) {
                        stringResource(
                            R.string.ssh_passphrase_remove_hint
                        )
                    } else {
                        null
                    }
                ) {
                    IosTextField(
                        current,
                        { current = it },
                        stringResource(R.string.ssh_field_passphrase_current),
                        secure = true,
                        showSeparator = false
                    )
                }
            }
            if (!removing) {
                NewPassphraseSection(
                    passphrase = replacement,
                    confirmation = confirmation,
                    onPassphrase = { replacement = it },
                    onConfirmation = { confirmation = it },
                    hint = stringResource(R.string.ssh_passphrase_hint)
                )
            }
        }
    }
}
