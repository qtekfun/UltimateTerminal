// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.backup.BackupMessage
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupPhase
import com.qtekfun.ultimateterminal.domain.distro.ExtractionError

@StringRes
internal fun BackupPhase.labelRes(): Int = when (this) {
    BackupPhase.PREPARING -> R.string.backup_phase_preparing
    BackupPhase.ARCHIVING -> R.string.backup_phase_archiving
    BackupPhase.WRITING -> R.string.backup_phase_writing
    BackupPhase.VERIFYING -> R.string.backup_phase_verifying
    BackupPhase.RESTORING -> R.string.backup_phase_restoring
    BackupPhase.APPLYING -> R.string.backup_phase_applying
}

/** The text the user reads for the result of a backup action. */
@Composable
internal fun backupMessageText(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> {
        val summary = message.summary
        val size = formatSize(LocalContext.current, summary.bytesWritten)
        val format = if (summary.encrypted) {
            R.string.backup_exported_encrypted
        } else {
            R.string.backup_exported
        }
        stringResource(
            format,
            message.fileName ?: stringResource(R.string.backup_file_unnamed),
            size,
            summary.distros
        )
    }

    is BackupMessage.Restored -> with(message.summary) {
        stringResource(
            R.string.backup_restored,
            distros,
            profiles,
            layouts,
            sshHosts,
            sshKeys,
            skipped
        )
    }

    is BackupMessage.Failed -> backupErrorText(LocalContext.current, message.error)
}

private fun backupErrorText(context: Context, error: BackupError): String = when (error) {
    BackupError.NotABackup -> context.getString(R.string.backup_error_not_a_backup)

    BackupError.PasswordRequired -> context.getString(R.string.backup_error_password_required)

    BackupError.WrongPasswordOrCorrupt -> context.getString(R.string.backup_error_wrong_password)

    BackupError.Truncated -> context.getString(R.string.backup_error_truncated)

    is BackupError.UnsupportedVersion -> context.getString(
        R.string.backup_error_version,
        error.found
    )

    is BackupError.InvalidManifest -> context.getString(
        R.string.backup_error_manifest,
        error.reason
    )

    is BackupError.InvalidConfig -> context.getString(R.string.backup_error_config)

    is BackupError.HashMismatch -> context.getString(R.string.backup_error_hash, error.part)

    else -> operationErrorText(context, error)
}

/** The errors of what the backup holds or of the device, rather than of the file's format. */
private fun operationErrorText(context: Context, error: BackupError): String = when (error) {
    BackupError.KeysNeedPassword -> context.getString(R.string.backup_error_keys_need_password)

    BackupError.DistroUnavailable -> context.getString(R.string.backup_error_distro_unavailable)

    is BackupError.InsufficientSpace -> context.getString(
        R.string.backup_error_space,
        formatSize(context, error.requiredBytes),
        formatSize(context, error.availableBytes)
    )

    is BackupError.UnreadableFile -> context.getString(
        if (error.denied) {
            R.string.backup_error_unreadable_denied
        } else {
            R.string.backup_error_unreadable
        },
        error.path
    )

    is BackupError.Extraction -> context.getString(
        R.string.backup_error_extraction,
        error.error.describe()
    )

    is BackupError.Io -> context.getString(R.string.backup_error_io, error.message)

    else -> context.getString(R.string.backup_error_io, error.toString())
}

private fun ExtractionError.describe(): String = when (this) {
    is ExtractionError.UnsafeEntry -> "$name: $reason"
    is ExtractionError.Corrupt -> reason
    is ExtractionError.TooLarge -> reason
    is ExtractionError.UnsupportedFormat -> format
    ExtractionError.NoSpace -> "no space left"
    is ExtractionError.Io -> message
}
