// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Duplicating, measuring and deleting a distro that has files its owner cannot read. */
class FileTreesUnreadableTest {
    @TempDir
    lateinit var dir: File

    private val src get() = File(dir, "src").toPath()
    private val dst get() = File(dir, "dst").toPath()

    @BeforeEach
    fun tree() {
        assumeFalse(Files.getAttribute(dir.toPath(), "unix:uid") == 0)
        File(src.toFile(), "etc").mkdirs()
        File(src.toFile(), "closed/inner").mkdirs()
        File(src.toFile(), "etc/shadow").writeText("root:*")
        File(src.toFile(), "closed/inner/f").writeText("abc")
        Files.createSymbolicLink(File(src.toFile(), "etc/dangling").toPath(), Paths.get("/no/such"))
        OwnerAccess.setMode(File(src.toFile(), "etc/shadow").toPath(), 0)
        OwnerAccess.setMode(File(src.toFile(), "closed/inner").toPath(), 0)
        OwnerAccess.setMode(File(src.toFile(), "closed").toPath(), 0)
    }

    @AfterEach
    fun cleanUp() {
        if (src.toFile().exists()) FileTrees.delete(src)
        if (dst.toFile().exists()) FileTrees.delete(dst)
    }

    @Test
    fun aCopyKeepsTheModesOfFilesAndDirectoriesTheAppCouldNotRead() {
        FileTrees.copy(src, dst)

        val shadow = File(dst.toFile(), "etc/shadow").toPath()
        assertEquals(0, OwnerAccess.modeOf(shadow))
        assertEquals(0, OwnerAccess.modeOf(File(dst.toFile(), "closed").toPath()))
        // Open the closed directory to look inside, as the app does.
        val closed = File(dst.toFile(), "closed")
        closed.setExecutable(true, true)
        assertEquals(0, OwnerAccess.modeOf(File(closed, "inner").toPath()))
        // The source is as it was.
        assertEquals(0, OwnerAccess.modeOf(File(src.toFile(), "etc/shadow").toPath()))
        assertEquals(0, OwnerAccess.modeOf(File(src.toFile(), "closed").toPath()))
        assertEquals(
            "/no/such",
            Files.readSymbolicLink(File(dst.toFile(), "etc/dangling").toPath()).toString()
        )
        // The content got across: open the copy to see it.
        shadow.toFile().setReadable(true, true)
        assertEquals("root:*", Files.readString(shadow))
    }

    @Test
    fun theSizeCountsFilesInClosedDirectoriesToo() {
        assertEquals(9L, FileTrees.size(src))
        assertEquals(0, OwnerAccess.modeOf(File(src.toFile(), "closed").toPath()))
    }

    @Test
    fun aTreeWithClosedDirectoriesIsDeleted() {
        FileTrees.delete(src)
        assertFalse(src.toFile().exists())
    }
}
