// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.FsPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NioSecretFileStoreTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path
    private lateinit var store: NioSecretFileStore

    @BeforeEach
    fun setUp() {
        root = Files.createDirectories(tempDir.resolve("storage"))
        store = NioSecretFileStore(root, Dispatchers.Unconfined)
    }

    private fun path(raw: String): FsPath = (FsPath.of(raw) as Outcome.Success).value

    private fun permissions(file: Path) =
        PosixFilePermissions.toString(Files.getPosixFilePermissions(file))

    @Test
    fun aWrittenFileIsReadBackAndDirectoriesAreCreated() = runTest {
        assertEquals(
            Outcome.Success(Unit),
            store.write(path("a/b/c.key"), "secret".toByteArray(), ownerOnly = true)
        )
        assertArrayEquals(
            "secret".toByteArray(),
            (store.read(path("a/b/c.key")) as Outcome.Success).value
        )
    }

    @Test
    fun anOwnerOnlyFileIsNotReadableByAnyoneElse() = runTest {
        store.write(path("k.key"), ByteArray(4), ownerOnly = true)
        assertEquals("rw-------", permissions(root.resolve("k.key")))
    }

    @Test
    fun overwritingKeepsOwnerOnlyAndReplacesTheContent() = runTest {
        store.write(path("k.key"), "one".toByteArray(), ownerOnly = true)
        store.write(path("k.key"), "two".toByteArray(), ownerOnly = true)
        assertEquals("two", String((store.read(path("k.key")) as Outcome.Success).value))
        assertEquals("rw-------", permissions(root.resolve("k.key")))
    }

    @Test
    fun noPartialFileIsLeftBehind() = runTest {
        store.write(path("dir/k.key"), ByteArray(8), ownerOnly = true)
        assertEquals(
            listOf("k.key"),
            Files.list(root.resolve("dir")).use { s ->
                s.map { it.fileName.toString() }.toList()
            }
        )
    }

    @Test
    fun aFileWrittenWithoutOwnerOnlyUsesTheDefaultPermissions() = runTest {
        store.write(path("plain.txt"), ByteArray(1), ownerOnly = false)
        assertTrue(Files.exists(root.resolve("plain.txt")))
    }

    @Test
    fun readingAMissingFileOrADirectoryIsNotFound() = runTest {
        assertEquals(Outcome.Failure(DomainError.NotFound), store.read(path("missing")))
        Files.createDirectories(root.resolve("dir"))
        assertEquals(Outcome.Failure(DomainError.NotFound), store.read(path("dir")))
    }

    @Test
    fun listNamesGivesTheDirectFilesSortedAndEmptyWhenMissing() = runTest {
        store.write(path("d/b"), ByteArray(1), ownerOnly = false)
        store.write(path("d/a"), ByteArray(1), ownerOnly = false)
        store.write(path("d/sub/c"), ByteArray(1), ownerOnly = false)
        assertEquals(listOf("a", "b", "sub"), (store.listNames(path("d")) as Outcome.Success).value)
        assertEquals(emptyList<String>(), (store.listNames(path("nope")) as Outcome.Success).value)
    }

    @Test
    fun deleteRemovesAFileAndIsFineWhenItIsGone() = runTest {
        store.write(path("k"), ByteArray(1), ownerOnly = false)
        assertEquals(Outcome.Success(Unit), store.delete(path("k")))
        assertFalse(Files.exists(root.resolve("k")))
        assertEquals(Outcome.Success(Unit), store.delete(path("k")))
    }

    @Test
    fun aSymbolicLinkOnTheWayNeverLeadsOutOfTheStorage() = runTest {
        val outside = Files.createDirectories(tempDir.resolve("outside"))
        Files.createSymbolicLink(root.resolve("tmp"), outside)
        val result = store.write(path("tmp/.ut-ssh-key"), ByteArray(4), ownerOnly = true)
        assertInstanceOf(Outcome.Failure::class.java, result)
        assertFalse(Files.exists(outside.resolve(".ut-ssh-key")))
        assertInstanceOf(Outcome.Failure::class.java, store.read(path("tmp/.ut-ssh-key")))
        assertInstanceOf(Outcome.Failure::class.java, store.delete(path("tmp/.ut-ssh-key")))
    }

    @Test
    fun aDanglingLinkAtTheTargetIsReplacedNotFollowed() = runTest {
        val outside = tempDir.resolve("outside.txt")
        Files.createSymbolicLink(root.resolve("etc-link"), outside)
        store.write(path("etc-link"), "x".toByteArray(), ownerOnly = false)
        assertFalse(Files.exists(outside))
        assertFalse(Files.isSymbolicLink(root.resolve("etc-link")))
    }

    @Test
    fun anIoFailureIsAnOutcomeNotAnException() = runTest {
        // A file where a directory is needed makes the write fail.
        Files.writeString(root.resolve("blocker"), "x")
        val result = store.write(path("blocker/inner"), ByteArray(1), ownerOnly = false)
        assertInstanceOf(Outcome.Failure::class.java, result)
    }
}
