// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.backup

import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import java.io.InputStream
import java.io.OutputStream

/** What a backup holds (SPEC RF-06): one distro, only the app's configuration, or everything. */
enum class BackupKind { CONFIG, DISTRO, ALL }

/**
 * What to export. [distroId] is required for [BackupKind.DISTRO] and ignored otherwise. A
 * [password] makes the file encrypted; without one the file is a plain archive anyone can read, so
 * SSH private keys are only exported with a password (or left out with [includeSshKeys] = false).
 */
data class ExportRequest(
    val kind: BackupKind,
    val distroId: Long? = null,
    val includeSshKeys: Boolean = true,
    val password: String? = null
)

/** Where a backup is read from. [open] is called once per pass, so it must give a fresh stream. */
interface BackupSource {
    suspend fun open(): InputStream
}

/** Where a backup is written; [discard] removes a partly written file after a failure. */
interface BackupSink {
    suspend fun open(): OutputStream

    suspend fun discard()
}

enum class BackupPhase { PREPARING, ARCHIVING, WRITING, VERIFYING, RESTORING, APPLYING }

data class BackupProgress(val phase: BackupPhase, val fraction: Float? = null)

data class ExportSummary(
    val kind: BackupKind,
    val distros: Int,
    val sshKeys: Int,
    val encrypted: Boolean,
    val bytesWritten: Long
)

/** What a restore did. Items that already existed (same name) are kept and counted as skipped. */
data class RestoreSummary(
    val distros: Int,
    val settingsApplied: Boolean,
    val profiles: Int,
    val layouts: Int,
    val sshHosts: Int,
    val sshKeys: Int,
    val skipped: Int
)

/** What can be known about a file before asking for its password. */
data class BackupProbe(val encrypted: Boolean)

sealed interface BackupError {
    /** The file is not a backup of this app. */
    data object NotABackup : BackupError

    /** The file is encrypted and no password was given. */
    data object PasswordRequired : BackupError

    /**
     * Authentication failed: the password is wrong or the file was changed or cut short. They
     * cannot be told apart, and nothing was written either way.
     */
    data object WrongPasswordOrCorrupt : BackupError

    /** The file ends before it should. */
    data object Truncated : BackupError

    /** A backup made by a newer version of the format. */
    data class UnsupportedVersion(val found: Int) : BackupError

    data class InvalidManifest(val reason: String) : BackupError

    data class InvalidConfig(val reason: String) : BackupError

    /** A part does not match the hash or the size the manifest promised. */
    data class HashMismatch(val part: String) : BackupError

    /** Private keys would be written unprotected; give a password or leave the keys out. */
    data object KeysNeedPassword : BackupError

    /** The distro to export does not exist or is not ready. */
    data object DistroUnavailable : BackupError

    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) : BackupError

    data class Extraction(val error: ExtractionError) : BackupError

    data class Io(val message: String) : BackupError
}

sealed interface BackupResult<out T> {
    data class Success<out T>(val value: T) : BackupResult<T>

    data class Failure(val error: BackupError) : BackupResult<Nothing>
}
