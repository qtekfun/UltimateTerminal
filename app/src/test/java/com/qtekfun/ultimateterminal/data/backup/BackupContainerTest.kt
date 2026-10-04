// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupContainerTest {
    private fun container(block: (ContainerWriter) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        ContainerWriter(out).use(block)
        return out.toByteArray()
    }

    @Test
    fun partsComeBackInOrderWithTheirContent() {
        val bytes = container {
            it.bytes("manifest.json", "{}".toByteArray())
            it.stream("distros/0.tar.gz", 5, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))
        }
        ContainerReader(ByteArrayInputStream(bytes)).use { reader ->
            val first = reader.next()!!
            assertEquals("manifest.json", first.name)
            assertEquals(2, first.size)
            assertEquals("{}", String(first.content.readBytes()))
            first.content.close() // closing a part must not end the archive
            val second = reader.next()!!
            assertEquals("distros/0.tar.gz", second.name)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), second.content.readBytes())
            assertNull(reader.next())
        }
    }

    @Test
    fun anEntryThisAppDoesNotWriteMakesTheFileNotABackup() {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(out).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("etc/passwd").also { it.size = 0 })
            tar.closeArchiveEntry()
        }
        val reader = ContainerReader(ByteArrayInputStream(out.toByteArray()))
        assertEquals(BackupError.NotABackup, assertThrows<BackupFailure> { reader.next() }.error)
    }

    @Test
    fun aDirectoryEntryIsNotAPart() {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(out).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("distros/"))
            tar.closeArchiveEntry()
        }
        val reader = ContainerReader(ByteArrayInputStream(out.toByteArray()))
        assertEquals(BackupError.NotABackup, assertThrows<BackupFailure> { reader.next() }.error)
    }

    @Test
    fun failuresAreMappedToTheErrorTheyStandFor() {
        assertEquals(BackupError.Truncated, BackupFailure(BackupError.Truncated).toBackupError())
        assertEquals(BackupError.Truncated, EOFException().toBackupError())
        assertEquals(BackupError.Truncated, IOException("Truncated TAR archive").toBackupError())
        assertEquals(
            BackupError.Truncated,
            IOException("Unexpected EOF in archive").toBackupError()
        )
        assertEquals(BackupError.NotABackup, IOException("Corrupted TAR archive.").toBackupError())
        assertEquals(
            BackupError.Io("No space left on device"),
            IOException("No space left on device").toBackupError()
        )
        assertEquals(BackupError.Io("IOException"), IOException().toBackupError())
    }
}
