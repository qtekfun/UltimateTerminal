// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.backup

enum class ExportFormProblem { NO_DISTRO, PASSWORD_MISMATCH, KEYS_NEED_PASSWORD }

/**
 * The rules of the export dialog, kept out of the screen: what makes a choice invalid and the
 * request a valid one stands for.
 */
object ExportForm {
    fun problem(
        kind: BackupKind,
        distroId: Long?,
        includeKeys: Boolean,
        password: String,
        repeat: String
    ): ExportFormProblem? = when {
        kind == BackupKind.DISTRO && distroId == null -> ExportFormProblem.NO_DISTRO
        password != repeat -> ExportFormProblem.PASSWORD_MISMATCH
        carriesKeys(kind, includeKeys) && password.isEmpty() -> ExportFormProblem.KEYS_NEED_PASSWORD
        else -> null
    }

    /** A request for a choice that [problem] accepted. */
    fun request(
        kind: BackupKind,
        distroId: Long?,
        includeKeys: Boolean,
        password: String
    ): ExportRequest = ExportRequest(
        kind = kind,
        distroId = distroId.takeIf { kind == BackupKind.DISTRO },
        includeSshKeys = carriesKeys(kind, includeKeys),
        password = password.ifEmpty { null }
    )

    /** A backup of one distro holds no configuration, so no keys. */
    private fun carriesKeys(kind: BackupKind, includeKeys: Boolean) =
        includeKeys && kind != BackupKind.DISTRO
}
