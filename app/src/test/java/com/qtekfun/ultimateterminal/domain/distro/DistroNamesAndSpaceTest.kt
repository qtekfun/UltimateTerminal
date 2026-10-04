// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DistroNamesAndSpaceTest {
    @Test
    fun aFreeNameIsKept() {
        assertEquals("Debian", DistroNames.suggest("Debian", listOf("Alpine")))
    }

    @Test
    fun aTakenNameGetsTheFirstFreeNumber() {
        assertEquals("Debian 2", DistroNames.suggest("Debian", listOf("Debian")))
        assertEquals(
            "Debian 4",
            DistroNames.suggest("Debian", listOf("debian", "DEBIAN 2", "Debian 3"))
        )
    }

    @Test
    fun aCopyIsNamedAfterTheOriginal() {
        assertEquals("Work copy", DistroNames.suggestCopy("Work", listOf("Work")))
        assertEquals(
            "Work copy 2",
            DistroNames.suggestCopy("Work", listOf("Work", "Work copy"))
        )
    }

    @Test
    fun theRequiredSpaceGrowsWithTheArchiveButHasAFloor() {
        val mib = 1024L * 1024
        assertEquals(64 * mib, InstallSpace.requiredBytes(3 * mib))
        assertEquals(5 * 100 * mib, InstallSpace.requiredBytes(100 * mib))
    }

    @Test
    fun anUnknownArchiveSizeAssumesAGenerousOne() {
        val mib = 1024L * 1024
        assertEquals(512 * mib, InstallSpace.requiredBytes(null))
        assertEquals(512 * mib, InstallSpace.requiredBytes(0))
    }

    @Test
    fun everyInstallHasItsOwnDirectories() {
        assertEquals("distros/abc", DistroPaths.distroDirectory("abc").value)
        assertEquals("distros-tmp/abc", DistroPaths.stagingDirectory("abc").value)
        assertEquals("distros-tmp", DistroPaths.stagingRoot().value)
    }
}
