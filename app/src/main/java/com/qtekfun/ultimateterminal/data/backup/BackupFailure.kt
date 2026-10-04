// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import java.io.EOFException
import java.io.IOException

/** Carries a [BackupError] out of a stream, where only an [IOException] can travel. */
internal class BackupFailure(val error: BackupError) : IOException(error.toString())

/** The error a failed read or write stands for; the stream classes throw [BackupFailure]. */
internal fun IOException.toBackupError(): BackupError {
    val text = message.orEmpty()
    return when {
        this is BackupFailure -> error

        // Commons Compress reports a cut-short tar in several ways.
        this is EOFException || text.contains("Truncated", true) ||
            text.contains("Unexpected EOF", true) -> BackupError.Truncated

        text.contains("Corrupted TAR", true) -> BackupError.NotABackup

        else -> BackupError.Io(text.ifEmpty { javaClass.simpleName })
    }
}
