// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.ssh.SshMessage

@StringRes
private fun SshError.messageRes(): Int = when (this) {
    is SshError.Invalid -> R.string.ssh_err_invalid
    SshError.UnsupportedKeyFormat -> R.string.ssh_err_format
    SshError.EncryptedKey -> R.string.ssh_err_encrypted
    SshError.CorruptKey -> R.string.ssh_err_corrupt
    is SshError.UnsupportedKeyType -> R.string.ssh_err_key_type
    is SshError.NameTaken -> R.string.ssh_err_name_taken
    SshError.KeyNotFound -> R.string.ssh_err_key_missing
    is SshError.KeyInUse -> R.string.ssh_err_key_in_use
    SshError.HostNotFound -> R.string.ssh_err_host_missing
    SshError.NoDistro -> R.string.ssh_err_no_distro
    is SshError.Storage -> R.string.ssh_err_storage
}

@StringRes
private fun DomainError.hostMessageRes(): Int = when (this) {
    is DomainError.NameTaken -> R.string.ssh_err_host_name_taken
    is DomainError.InvalidName, is DomainError.InvalidValue -> R.string.ssh_err_host_invalid
    else -> R.string.err_action
}

@Composable
internal fun SshMessage.text(): String = when (this) {
    is SshMessage.KeyAdded -> stringResource(R.string.ssh_msg_key_added, name)
    is SshMessage.Failed -> stringResource(error.messageRes())
    is SshMessage.HostFailed -> stringResource(error.hostMessageRes())
    SshMessage.PrivateKeySaved -> stringResource(R.string.ssh_msg_private_saved)
    SshMessage.PublicKeyCopied -> stringResource(R.string.ssh_msg_public_copied)
}
