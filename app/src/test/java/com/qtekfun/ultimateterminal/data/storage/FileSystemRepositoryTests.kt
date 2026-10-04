// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NioFileSystemRepositoryTest : FileSystemRepositoryContract() {
    @TempDir
    lateinit var tempDir: Path

    private val root: Path get() = tempDir.resolve("storage")

    override fun create(): FileSystemRepository {
        Files.createDirectories(root)
        return NioFileSystemRepository(root, Dispatchers.Unconfined, JVM_FREE_SPACE)
    }

    override fun put(path: String, content: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    override fun read(path: String): String? = root.resolve(path).takeIf {
        Files.isRegularFile(it)
    }?.let(Files::readString)

    @Test
    fun `freeSpaceBytes reports the free space of the storage`() = runTest {
        assertTrue(fs.freeSpaceBytes() > 0L)
    }

    @Test
    fun `the repository never asks for a FileStore, which Android denies to apps`() {
        // Files.getFileStore reads /proc/mounts: fine on the JVM, SecurityException on a device
        // (it crashed every distro install on a Pixel 8), so host tests cannot see it.
        val source = java.io.File(
            "src/main/java/com/qtekfun/ultimateterminal/data/storage/NioFileSystemRepository.kt"
        ).readText()

        assertFalse(
            source.lines().any {
                it.contains("Files.getFileStore(") &&
                    !it.trim().startsWith("*")
            }
        )
    }

    @Test
    fun `freeSpaceBytes is zero when the probe is refused or the path is invalid`() = runTest {
        val denied =
            NioFileSystemRepository(root, Dispatchers.Unconfined) {
                throw SecurityException("denied")
            }
        val invalid =
            NioFileSystemRepository(root, Dispatchers.Unconfined) {
                throw IllegalArgumentException("gone")
            }

        assertEquals(0L, denied.freeSpaceBytes())
        assertEquals(0L, invalid.freeSpaceBytes())
    }

    @Test
    fun `freeSpaceBytes is zero when the storage is missing`() = runTest {
        val missing =
            NioFileSystemRepository(tempDir.resolve("gone"), Dispatchers.Unconfined, JVM_FREE_SPACE)

        assertEquals(0L, missing.freeSpaceBytes())
    }

    @Test
    fun `absolutePathOf is the real location`() {
        assertEquals(root.resolve("distros/a").toString(), fs.absolutePathOf(path("distros/a")))
    }

    @Test
    fun `symbolic links are copied as links and never followed`() = runTest {
        val outside = Files.createDirectories(tempDir.resolve("outside"))
        Files.writeString(outside.resolve("secret.txt"), "secret")
        put("src/real.txt", "r")
        Files.createSymbolicLink(root.resolve("src/rel"), Path.of("real.txt"))
        Files.createSymbolicLink(root.resolve("src/abs"), outside)

        assertEquals(Outcome.Success(Unit), fs.copyRecursively(path("src"), path("dst")))

        assertEquals(Path.of("real.txt"), Files.readSymbolicLink(root.resolve("dst/rel")))
        assertEquals(outside, Files.readSymbolicLink(root.resolve("dst/abs")))
        assertEquals("secret", Files.readString(outside.resolve("secret.txt")))
    }

    @Test
    fun `copyRecursively keeps permissions of executables and read-only directories`() = runTest {
        put("src/bin/tool", "#!/bin/sh")
        Files.setPosixFilePermissions(
            root.resolve("src/bin/tool"),
            PosixFilePermissions.fromString("rwxr-xr-x")
        )
        Files.setPosixFilePermissions(
            root.resolve("src/bin"),
            PosixFilePermissions.fromString("r-xr-xr-x")
        )

        try {
            assertEquals(Outcome.Success(Unit), fs.copyRecursively(path("src"), path("dst")))

            assertEquals(
                PosixFilePermissions.fromString("rwxr-xr-x"),
                Files.getPosixFilePermissions(root.resolve("dst/bin/tool"))
            )
            assertEquals(
                PosixFilePermissions.fromString("r-xr-xr-x"),
                Files.getPosixFilePermissions(root.resolve("dst/bin"))
            )
        } finally {
            // Let JUnit clean the temporary directory.
            listOf("src/bin", "dst/bin").forEach {
                runCatching {
                    Files.setPosixFilePermissions(
                        root.resolve(it),
                        PosixFilePermissions.fromString("rwxr-xr-x")
                    )
                }
            }
        }
    }

    @Test
    fun `deleteRecursively removes read-only directories and ignores links out of the tree`() =
        runTest {
            val outside = Files.createDirectories(tempDir.resolve("outside"))
            Files.writeString(outside.resolve("keep.txt"), "keep")
            put("d/ro/file.txt", "x")
            Files.createSymbolicLink(root.resolve("d/link"), outside)
            Files.setPosixFilePermissions(
                root.resolve("d/ro"),
                PosixFilePermissions.fromString("r-xr-xr-x")
            )

            assertEquals(Outcome.Success(Unit), fs.deleteRecursively(path("d")))

            assertFalse(Files.exists(root.resolve("d")))
            assertEquals("keep", Files.readString(outside.resolve("keep.txt")))
        }

    @Test
    fun `a path that goes through a symbolic link is refused for every operation`() = runTest {
        val outside = Files.createDirectories(tempDir.resolve("outside"))
        Files.writeString(outside.resolve("victim.txt"), "victim")
        put("rootfs/real.txt", "r")
        Files.createSymbolicLink(root.resolve("rootfs/etc"), outside)
        val through = path("rootfs/etc/victim.txt")
        val refused = Outcome.Failure(DomainError.InvalidPath("rootfs/etc/victim.txt"))

        assertEquals(refused, fs.deleteRecursively(through))
        assertEquals(refused, fs.createDirectories(path("rootfs/etc/victim.txt")))
        assertEquals(refused, fs.sizeOf(through))
        assertEquals(refused, fs.copyRecursively(through, path("stolen")))
        assertEquals(refused, fs.copyRecursively(path("rootfs/real.txt"), through))
        assertEquals(refused, fs.move(through, path("stolen")))
        assertEquals(refused, fs.move(path("rootfs/real.txt"), through))
        assertFalse(fs.exists(through))
        assertEquals("victim", Files.readString(outside.resolve("victim.txt")))
        assertFalse(Files.exists(root.resolve("stolen")))
    }

    @Test
    fun `a link itself can be deleted and measured without touching its target`() = runTest {
        val outside = Files.createDirectories(tempDir.resolve("outside"))
        Files.writeString(outside.resolve("big.txt"), "123456")
        put("rootfs/a.txt", "1")
        Files.createSymbolicLink(root.resolve("rootfs/link"), outside)

        assertEquals(Outcome.Success(1L), fs.sizeOf(path("rootfs")))
        assertEquals(Outcome.Success(Unit), fs.deleteRecursively(path("rootfs/link")))

        assertFalse(Files.exists(root.resolve("rootfs/link"), LinkOption.NOFOLLOW_LINKS))
        assertEquals("123456", Files.readString(outside.resolve("big.txt")))
    }

    @Test
    fun `a failed copy leaves neither the destination nor a half-built copy`() = runTest {
        put("src/ok.txt", "ok")
        put("src/locked/secret.txt", "s")
        val locked = root.resolve("src/locked")
        Files.setPosixFilePermissions(locked, emptySet<PosixFilePermission>())

        try {
            val result = fs.copyRecursively(path("src"), path("dst"))

            // A user who can read everything (root) would succeed; then there is nothing to check.
            if (result is Outcome.Failure) {
                assertTrue(result.error is DomainError.Io)
                assertFalse(Files.exists(root.resolve("dst")))
                assertFalse(Files.exists(root.resolve("dst.partial")))
            }
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"))
        }
    }

    @Test
    fun `a leftover half-built copy from an earlier crash is replaced`() = runTest {
        put("src/a.txt", "new")
        put("dst.partial/stale.txt", "stale")

        assertEquals(Outcome.Success(Unit), fs.copyRecursively(path("src"), path("dst")))

        assertEquals("new", read("dst/a.txt"))
        assertFalse(Files.exists(root.resolve("dst/stale.txt")))
        assertFalse(Files.exists(root.resolve("dst.partial")))
    }

    @Test
    fun `special files are skipped when copying`() = runTest {
        put("src/a.txt", "a")
        val fifo = root.resolve("src/pipe")
        val made = ProcessBuilder("mkfifo", fifo.toString()).start().waitFor() == 0

        assertEquals(Outcome.Success(Unit), fs.copyRecursively(path("src"), path("dst")))

        assertEquals("a", read("dst/a.txt"))
        if (made) assertFalse(Files.exists(root.resolve("dst/pipe")))
    }

    @Test
    fun `directory times are kept`() = runTest {
        put("src/a.txt", "a")
        val time = FileTime.fromMillis(1_000_000_000_000L)
        Files.setLastModifiedTime(root.resolve("src"), time)

        fs.copyRecursively(path("src"), path("dst"))

        assertEquals(time, Files.getLastModifiedTime(root.resolve("dst")))
    }
}

class InMemoryFileSystemRepositoryTest : FileSystemRepositoryContract() {
    private lateinit var memory: InMemoryFileSystemRepository

    override fun create(): FileSystemRepository {
        memory = InMemoryFileSystemRepository()
        return memory
    }

    override fun put(path: String, content: String) = memory.putFile(path, content.toByteArray())

    override fun read(path: String): String? = memory.readFile(path)?.decodeToString()

    @Test
    fun `free space is what was configured`() = runTest {
        assertEquals(42L, InMemoryFileSystemRepository(freeSpace = 42L).freeSpaceBytes())
    }
}

/** The JVM stand-in for `StatFs`: tests run on the host, where `android.os` is not available. */
private val JVM_FREE_SPACE: (Path) -> Long = { it.toFile().usableSpace }
