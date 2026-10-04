// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.distro.DistroMessage
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import com.qtekfun.ultimateterminal.domain.distro.InstallError
import com.qtekfun.ultimateterminal.domain.distro.InstallPhase
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError

@StringRes
internal fun InstallPhase.labelRes(): Int = when (this) {
    InstallPhase.RESOLVING -> R.string.install_phase_resolving
    InstallPhase.DOWNLOADING -> R.string.install_phase_downloading
    InstallPhase.VERIFYING -> R.string.install_phase_verifying
    InstallPhase.EXTRACTING -> R.string.install_phase_extracting
    InstallPhase.FINALIZING -> R.string.install_phase_finalizing
}

@StringRes
internal fun DistroFamily.nameRes(): Int = when (this) {
    DistroFamily.DEBIAN -> R.string.family_debian
    DistroFamily.UBUNTU -> R.string.family_ubuntu
    DistroFamily.ALPINE -> R.string.family_alpine
}

@StringRes
internal fun DistroType.nameRes(): Int = when (this) {
    DistroType.DEBIAN -> R.string.family_debian
    DistroType.UBUNTU -> R.string.family_ubuntu
    DistroType.ALPINE -> R.string.family_alpine
}

internal fun formatSize(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes)

/** The text the user reads for a message of the distro screen. */
@Composable
internal fun messageText(message: DistroMessage): String {
    val context = LocalContext.current
    return when (message) {
        is DistroMessage.Installed -> stringResource(R.string.msg_installed, message.name)

        is DistroMessage.ActionFailed -> stringResource(message.error.messageRes())

        is DistroMessage.InstallFailed -> when (val error = message.error) {
            is InstallError.InsufficientSpace -> stringResource(
                R.string.err_space,
                formatSize(context, error.requiredBytes),
                formatSize(context, error.availableBytes)
            )

            else -> stringResource(error.messageRes())
        }
    }
}

@StringRes
private fun InstallError.messageRes(): Int = when (this) {
    is InstallError.UnsupportedArchitecture -> R.string.err_abi

    is InstallError.InvalidRequest -> error.messageRes()

    is InstallError.Catalog -> error.messageRes()

    is InstallError.Download -> error.messageRes()

    is InstallError.Extraction -> when (error) {
        is ExtractionError.UnsafeEntry -> R.string.err_unsafe
        else -> R.string.err_extract
    }

    is InstallError.InsufficientSpace -> R.string.err_generic

    is InstallError.Storage -> R.string.err_generic
}

@StringRes
private fun RootfsError.messageRes(): Int = when (this) {
    is RootfsError.HashMismatch, is RootfsError.SizeMismatch -> R.string.err_checksum

    is RootfsError.CatalogUnavailable,
    is RootfsError.HttpStatus,
    is RootfsError.Network -> R.string.err_network

    else -> R.string.err_generic
}

@StringRes
private fun DomainError.messageRes(): Int = when (this) {
    is DomainError.InvalidName, is DomainError.InvalidValue -> R.string.err_name_invalid
    is DomainError.NameTaken -> R.string.err_name_taken
    else -> R.string.err_action
}
