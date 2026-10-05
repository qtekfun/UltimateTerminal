// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.data.storage.FileTrees
import com.qtekfun.ultimateterminal.data.storage.OwnerAccess
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** A distro like Fedora, with files only root can read, exported and restored on another device. */
class BackupModesRoundTripTest {
    @TempDir
    lateinit var dir: File
    private lateinit var old: Device
    private lateinit var new: Device

    @BeforeEach
    fun setUp() {
        assumeFalse(Files.getAttribute(dir.toPath(), "unix:uid") == 0)
        old = Device(File(dir, "old"))
        new = Device(File(dir, "new"))
    }

    @AfterEach
    fun tearDown() {
        old.close()
        new.close()
        FileTrees.delete(File(dir, "old").toPath())
        FileTrees.delete(File(dir, "new").toPath())
    }

    private fun mode(root: File, name: String) = OwnerAccess.modeOf(File(root, name).toPath())

    @Test
    fun theModesOfUnreadableFilesSurviveAnExportAndARestore() = runBlocking {
        val fedora = old.addDistro("Fedora") { root ->
            File(root, "etc").mkdirs()
            File(root, "usr/bin").mkdirs()
            File(root, "etc/shadow").writeText("root:*:0")
            File(root, "etc/gshadow").writeText("root:::")
            File(root, "usr/bin/sudo").writeText("ELF")
            File(root, "etc/resolv.conf").writeText("nameserver 1.1.1.1")
            Files.createSymbolicLink(File(root, "etc/abs").toPath(), Paths.get("/etc/missing"))
            OwnerAccess.setMode(File(root, "etc/shadow").toPath(), 0)
            OwnerAccess.setMode(File(root, "etc/gshadow").toPath(), 0)
            OwnerAccess.setMode(File(root, "usr/bin/sudo").toPath(), 0b001_001_001)
            OwnerAccess.setMode(File(root, "etc/resolv.conf").toPath(), 0b110_100_100)
        }
        val bytes = old.exportBytes(ExportRequest(BackupKind.DISTRO, fedora.id))

        restoreOn(bytes)

        val copy = new.distroDir(new.distros.observeAll().first().single())
        listOf("etc/shadow", "etc/gshadow", "usr/bin/sudo", "etc/resolv.conf").forEach {
            assertEquals(mode(old.distroDir(fedora), it), mode(copy, it), it)
        }
        assertEquals(0, mode(copy, "etc/shadow"))
        assertEquals(0b001_001_001, mode(copy, "usr/bin/sudo"))
        assertEquals(
            "/etc/missing",
            Files.readSymbolicLink(File(copy, "etc/abs").toPath()).toString()
        )
        // The content is there too, and a second export of the restored distro reproduces it.
        val again = new.exportBytes(
            ExportRequest(BackupKind.DISTRO, new.distros.observeAll().first().single().id)
        )
        restoreOn(again, old)
        val third = old.distros.observeAll().first().first { it.name != "Fedora" }
        assertEquals(0, mode(old.distroDir(third), "etc/shadow"))
        assertEquals(0b001_001_001, mode(old.distroDir(third), "usr/bin/sudo"))
        File(copy, "etc/shadow").setReadable(true, true)
        assertEquals("root:*:0", File(copy, "etc/shadow").readText())
    }

    @Test
    fun aDirectoryTheAppCannotListIsExportedAndComesBackOpenForItsOwner() = runBlocking {
        val distro = old.addDistro("Closed") { root ->
            File(root, "vault").mkdirs()
            File(root, "vault/key").writeText("k")
            OwnerAccess.setMode(File(root, "vault").toPath(), 0)
        }
        val bytes = old.exportBytes(ExportRequest(BackupKind.DISTRO, distro.id))
        restoreOn(bytes)

        val copy = new.distroDir(new.distros.observeAll().first().single())
        // Directories always keep the owner's rwx on disk, so the app can move and delete them.
        assertEquals(0b111_000_000, mode(copy, "vault"))
        assertEquals("k", File(copy, "vault/key").readText())
    }

    private fun restoreOn(bytes: ByteArray, device: Device = new) = runBlocking {
        device.restorer().restore(BytesSource(bytes), null).value()
    }
}
