// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RootfsArchiverTest {
    @TempDir
    lateinit var dir: File

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
}
