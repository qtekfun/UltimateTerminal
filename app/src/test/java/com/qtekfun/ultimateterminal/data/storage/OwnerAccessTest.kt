// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Tests of what the app does with files it owns but cannot read (a distro's mode 000 files). */
class OwnerAccessTest {
    @TempDir
    lateinit var dir: File

    @BeforeEach
    fun notRoot() {
        // Root reads everything, so there would be nothing to test.
        assumeFalse(Files.getAttribute(dir.toPath(), "unix:uid") == 0)
    }

    private fun file(name: String, mode: Int, text: String = "secret"): Path {
        val path = File(dir, name).toPath()
        Files.writeString(path, text)
        OwnerAccess.setMode(path, mode)
        return path
    }

    @Test
    fun theTwelveModeBitsRoundTrip() {
        val path = file("sudo", 0b100_100_001_001)
        assertEquals(0b100_100_001_001, OwnerAccess.modeOf(path))
        OwnerAccess.setMode(path, 0b111_101_101_101)
        assertEquals(0b111_101_101_101, OwnerAccess.modeOf(path))
    }

    @Test
    fun aFileWithNoModeAtAllIsReadAndGetsItsModeBack() {
        val path = file("shadow", 0)
        val text = OwnerAccess.reading(path) { it.readBytes().decodeToString() }
        assertEquals("secret", text)
        assertEquals(0, OwnerAccess.modeOf(path))
    }

    @Test
    fun anExecuteOnlyFileKeepsItsSetuidBitAfterTheRead() {
        val path = file("sudo", 0b100_001_001_001)
        assertEquals(6, OwnerAccess.reading(path) { it.readBytes().size })
        assertEquals(0b100_001_001_001, OwnerAccess.modeOf(path))
    }

    @Test
    fun aFileThatIsAlreadyReadableIsNotTouched() {
        val path = file("motd", 0b110_100_100)
        OwnerAccess.reading(path) { it.readBytes() }
        assertEquals(0b110_100_100, OwnerAccess.modeOf(path))
    }

    @Test
    fun anErrorOfTheReaderItselfPassesThroughAndTheModeIsRestored() {
        val path = file("shadow", 0)
        val failure = assertThrows(IOException::class.java) {
            OwnerAccess.reading(path) { throw IOException("disk full") }
        }
        assertEquals("disk full", failure.message)
        assertEquals(0, OwnerAccess.modeOf(path))
    }

    @Test
    fun aFileThatStaysClosedIsReportedWithItsPathAndTheModeIsRestored() {
        val path = file("shadow", 0)
        val error = assertThrows(UnreadableFileException::class.java) {
            OwnerAccess.reading(path, { throw AccessDeniedException(it.toString()) }) { }
        }
        assertEquals(path, error.path)
        assertTrue(error.denied)
        assertEquals(0, OwnerAccess.modeOf(path))
    }

    @Test
    fun aFileThatVanishedIsReportedAsNotDenied() {
        val path = File(dir, "gone").toPath()
        val error = assertThrows(UnreadableFileException::class.java) {
            OwnerAccess.reading(path) { }
        }
        assertFalse(error.denied)
        assertTrue(error.cause is NoSuchFileException)
    }

    @Test
    fun aDirectoryWithoutAccessIsOpenedAndPutBack() {
        val sub = File(dir, "private").also { it.mkdirs() }.toPath()
        File(dir, "private/inner").writeText("x")
        OwnerAccess.setMode(sub, 0)
        val saved = OwnerAccess.openDirectory(sub)
        assertEquals(0, saved)
        assertEquals(listOf("inner"), sub.toFile().list()!!.toList())
        OwnerAccess.restore(sub, saved)
        assertEquals(0, OwnerAccess.modeOf(sub))
        assertTrue(sub.toFile().setReadable(true, true) && sub.toFile().setExecutable(true, true))
    }

    @Test
    fun aDirectoryThatCanBeListedIsLeftAlone() {
        val sub = File(dir, "open").also { it.mkdirs() }.toPath()
        assertNull(OwnerAccess.openDirectory(sub))
        OwnerAccess.restore(sub, null)
        assertEquals(0b111_101_101, OwnerAccess.modeOf(sub) and 0b111_101_101)
    }

    @Test
    fun aDirectoryThatCannotBeOpenedIsReportedWithItsPath() {
        val missing = File(dir, "missing").toPath()
        val error = assertThrows(UnreadableFileException::class.java) {
            OwnerAccess.openDirectory(missing)
        }
        assertEquals(missing, error.path)
    }

    @Test
    fun restoringAFileThatVanishedIsHarmless() {
        OwnerAccess.restore(File(dir, "gone").toPath(), 0b110_100_100)
    }
}
