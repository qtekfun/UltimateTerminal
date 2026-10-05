// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.data.storage.FileTrees
import com.qtekfun.ultimateterminal.data.storage.OwnerAccess
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RootfsArchiverTest {
    @TempDir
    lateinit var dir: File

    @AfterEach
    fun makeDeletable() {
        // Some tests leave directories closed, as a distro does; the owner can still delete them.
        File(dir, "rootfs").takeIf { it.exists() }?.let { FileTrees.delete(it.toPath()) }
    }

    private fun entries(bytes: ByteArray): Map<String, TarArchiveEntry> {
        val found = LinkedHashMap<String, TarArchiveEntry>()
        TarArchiveInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                found[entry.name] = entry
            }
        }
        return found
    }

    @Test
    fun filesDirectoriesAndLinksKeepTheirModesAndTargets() {
        val root = File(dir, "rootfs").also { it.mkdirs() }
        File(root, "bin").mkdirs()
        File(root, "bin/busybox").writeText("ELF")
        Files.setPosixFilePermissions(
            File(root, "bin/busybox").toPath(),
            PosixFilePermissions.fromString("rwxr-xr-x")
        )
        File(root, "etc").mkdirs()
        File(root, "etc/shadow").writeText("secret")
        Files.setPosixFilePermissions(
            File(root, "etc/shadow").toPath(),
            PosixFilePermissions.fromString("rw-------")
        )
        Files.createSymbolicLink(File(root, "bin/sh").toPath(), java.nio.file.Paths.get("busybox"))
        Files.createSymbolicLink(File(root, "broken").toPath(), java.nio.file.Paths.get("/nowhere"))

        val out = ByteArrayOutputStream()
        val reported = ArrayList<Long>()
        RootfsArchiver().write(root.toPath(), out) { reported.add(it) }
        val found = entries(out.toByteArray())

        assertEquals(
            setOf("./", "bin/", "bin/busybox", "bin/sh", "broken", "etc/", "etc/shadow"),
            found.keys
        )
        assertEquals(0b111_101_101, found.getValue("bin/busybox").mode)
        assertEquals(0b110_000_000, found.getValue("etc/shadow").mode)
        assertTrue(found.getValue("bin/sh").isSymbolicLink)
        assertEquals("busybox", found.getValue("bin/sh").linkName)
        assertEquals("/nowhere", found.getValue("broken").linkName)
        assertTrue(found.getValue("bin/").isDirectory)
        assertTrue(found.getValue("./").isDirectory)
        assertEquals(9L, reported.max())
    }

    @Test
    fun pipesAndOtherSpecialFilesAreSkipped() {
        val root = File(dir, "rootfs").also { it.mkdirs() }
        File(root, "plain").writeText("x")
        val made = ProcessBuilder("mkfifo", File(root, "pipe").path).start().waitFor()
        assertEquals(0, made, "mkfifo is needed to make a special file")
        val out = ByteArrayOutputStream()
        RootfsArchiver().write(root.toPath(), out) {}
        assertEquals(setOf("./", "plain"), entries(out.toByteArray()).keys)
    }

    @Test
    fun nonAsciiAndVeryLongNamesAreArchivedWithoutLosingThem() {
        val root = File(dir, "rootfs").also { it.mkdirs() }
        val long = "d".repeat(90)
        File(root, "$long/$long").mkdirs()
        File(root, "$long/$long/ñandú-é.txt").writeText("hola")
        val out = ByteArrayOutputStream()
        RootfsArchiver().write(root.toPath(), out) {}
        assertTrue("$long/$long/ñandú-é.txt" in entries(out.toByteArray()).keys)
    }

    private fun odd(root: File) {
        File(root, "etc").mkdirs()
        File(root, "usr/bin").mkdirs()
        File(root, "locked/inner").mkdirs()
        File(root, "etc/shadow").writeText("root:*:0")
        File(root, "usr/bin/sudo").writeText("ELF")
        File(root, "locked/inner/file").writeText("deep")
        Files.createSymbolicLink(File(root, "etc/abs").toPath(), Paths.get("/etc/ssl/missing.so"))
        Files.createSymbolicLink(File(root, "etc/up").toPath(), Paths.get("../usr"))
        Files.createLink(
            File(root, "usr/bin/sudoreplay").toPath(),
            File(root, "usr/bin/sudo").toPath()
        )
        OwnerAccess.setMode(File(root, "etc/shadow").toPath(), 0)
        OwnerAccess.setMode(File(root, "usr/bin/sudo").toPath(), 0b100_001_001_001)
        OwnerAccess.setMode(File(root, "locked/inner").toPath(), 0)
        OwnerAccess.setMode(File(root, "locked").toPath(), 0b001_000_000)
    }

    @Test
    fun filesAndDirectoriesTheOwnerCannotReadAreArchivedWithTheirOriginalModes() {
        assumeFalse(Files.getAttribute(dir.toPath(), "unix:uid") == 0)
        val root = File(dir, "rootfs").also { it.mkdirs() }
        odd(root)

        val out = ByteArrayOutputStream()
        RootfsArchiver().write(root.toPath(), out) {}
        val found = entries(out.toByteArray())

        assertEquals(0, found.getValue("etc/shadow").mode)
        assertEquals(0b100_001_001_001, found.getValue("usr/bin/sudo").mode)
        assertEquals(0b100_001_001_001, found.getValue("usr/bin/sudoreplay").mode)
        assertEquals(0, found.getValue("locked/inner/").mode)
        assertEquals(0b001_000_000, found.getValue("locked/").mode)
        assertTrue("locked/inner/file" in found.keys)
        assertEquals("/etc/ssl/missing.so", found.getValue("etc/abs").linkName)
        assertEquals("../usr", found.getValue("etc/up").linkName)
        // Nothing the walk opened stays open, and nothing is left changed on disk.
        assertEquals(0, OwnerAccess.modeOf(File(root, "etc/shadow").toPath()))
        assertEquals(0, OwnerAccess.modeOf(File(root, "locked/inner").toPath()))
        assertEquals(0b001_000_000, OwnerAccess.modeOf(File(root, "locked").toPath()))
        assertEquals(
            0b100_001_001_001,
            OwnerAccess.modeOf(File(root, "usr/bin/sudo").toPath())
        )
        assertEquals(
            0b111_111_111,
            OwnerAccess.modeOf(File(root, "etc/abs").toPath()) and 0b111_111_111
        )
    }

    @Test
    fun theContentOfAnUnreadableFileIsInTheArchive() {
        assumeFalse(Files.getAttribute(dir.toPath(), "unix:uid") == 0)
        val root = File(dir, "rootfs").also { it.mkdirs() }
        odd(root)
        val out = ByteArrayOutputStream()
        RootfsArchiver().write(root.toPath(), out) {}
        TarArchiveInputStream(GZIPInputStream(ByteArrayInputStream(out.toByteArray()))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.name ==
                    "etc/shadow"
                ) {
                    assertEquals("root:*:0", tar.readAllBytes().decodeToString())
                }
            }
        }
    }

    @Test
    fun aFileWhoseModeChangesWhileTheExportRunsIsStillRead() {
        assumeFalse(Files.getAttribute(dir.toPath(), "unix:uid") == 0)
        val root = File(dir, "rootfs").also { it.mkdirs() }
        val file = File(root, "log").also { it.writeText("data") }.toPath()
        var calls = 0
        val archiver = RootfsArchiver { path ->
            // The first open finds the file closed, as if something had just changed its mode.
            if (calls++ == 0) OwnerAccess.setMode(path, 0)
            Files.newInputStream(path)
        }
        val out = ByteArrayOutputStream()
        archiver.write(root.toPath(), out) {}
        assertEquals(setOf("./", "log"), entries(out.toByteArray()).keys)
        assertEquals(2, calls)
        assertEquals(0, OwnerAccess.modeOf(file))
    }

    @Test
    fun aFileThatCannotBeReadFailsTheExportAndNamesIt() {
        val root = File(dir, "rootfs").also { it.mkdirs() }
        File(root, "etc").mkdirs()
        File(root, "etc/shadow").writeText("x")
        val archiver = RootfsArchiver { throw AccessDeniedException(it.toString()) }
        val failure = assertThrows(BackupFailure::class.java) {
            archiver.write(root.toPath(), ByteArrayOutputStream()) {}
        }
        assertEquals(BackupError.UnreadableFile("etc/shadow", denied = true), failure.error)
    }
}
