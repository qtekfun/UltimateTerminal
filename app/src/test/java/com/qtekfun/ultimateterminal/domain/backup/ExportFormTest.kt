// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExportFormTest {
    private fun problem(
        kind: BackupKind = BackupKind.ALL,
        distroId: Long? = null,
        keys: Boolean = false,
        password: String = "",
        repeat: String = password
    ) = ExportForm.problem(kind, distroId, keys, password, repeat)

    @Test
    fun aDistroBackupNeedsADistro() {
        assertEquals(ExportFormProblem.NO_DISTRO, problem(BackupKind.DISTRO))
        assertNull(problem(BackupKind.DISTRO, distroId = 3))
    }

    @Test
    fun thePasswordMustBeRepeatedCorrectly() {
        assertEquals(ExportFormProblem.PASSWORD_MISMATCH, problem(password = "a", repeat = "b"))
        assertEquals(ExportFormProblem.PASSWORD_MISMATCH, problem(password = "", repeat = "b"))
        assertNull(problem(password = "a", repeat = "a"))
    }

    @Test
    fun keysNeedAPasswordExceptInADistroBackupWhichHasNone() {
        assertEquals(ExportFormProblem.KEYS_NEED_PASSWORD, problem(keys = true))
        assertEquals(ExportFormProblem.KEYS_NEED_PASSWORD, problem(BackupKind.CONFIG, keys = true))
        assertNull(problem(keys = true, password = "pw"))
        assertNull(problem(BackupKind.DISTRO, distroId = 1, keys = true))
        assertNull(problem(keys = false))
    }

    @Test
    fun theRequestMatchesTheChoice() {
        assertEquals(
            ExportRequest(BackupKind.ALL, null, includeSshKeys = true, password = "pw"),
            ExportForm.request(BackupKind.ALL, 9, true, "pw")
        )
        assertEquals(
            ExportRequest(BackupKind.DISTRO, 9, includeSshKeys = false, password = null),
            ExportForm.request(BackupKind.DISTRO, 9, true, "")
        )
        assertEquals(
            ExportRequest(BackupKind.CONFIG, null, includeSshKeys = false, password = null),
            ExportForm.request(BackupKind.CONFIG, 9, false, "")
        )
    }
}
