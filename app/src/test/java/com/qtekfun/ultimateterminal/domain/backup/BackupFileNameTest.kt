// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.backup

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupFileNameTest {
    private val day = LocalDate.of(2026, 10, 5)

    @Test
    fun aDistroBackupIsNamedAfterTheDistroAndTheDate() {
        assertEquals(
            "UltimateTerminal-Fedora-44-2026-10-05.utbackup",
            BackupFileName.suggest(BackupKind.DISTRO, "Fedora 44", day)
        )
    }

    @Test
    fun otherKindsHaveFixedLabels() {
        assertEquals(
            "UltimateTerminal-settings-2026-10-05.utbackup",
            BackupFileName.suggest(BackupKind.CONFIG, "ignored", day)
        )
        assertEquals(
            "UltimateTerminal-2026-10-05.utbackup",
            BackupFileName.suggest(BackupKind.ALL, null, day)
        )
    }

    @Test
    fun aNameWithOddCharactersIsMadeSafeAndShort() {
        assertEquals(
            "UltimateTerminal-a-b-c-2026-10-05.utbackup",
            BackupFileName.suggest(BackupKind.DISTRO, "/a: b/../c", day)
        )
        val long = BackupFileName.suggest(BackupKind.DISTRO, "x".repeat(100), day)
        assertEquals("UltimateTerminal-${"x".repeat(40)}-2026-10-05.utbackup", long)
        assertEquals(
            "UltimateTerminal-distro-2026-10-05.utbackup",
            BackupFileName.suggest(BackupKind.DISTRO, "???", day)
        )
        assertEquals(
            "UltimateTerminal-distro-2026-10-05.utbackup",
            BackupFileName.suggest(BackupKind.DISTRO, null, day)
        )
    }
}
