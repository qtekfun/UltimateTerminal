// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** What every [FileSystemRepository] must do; runs against the real one and the in-memory fake. */
abstract class FileSystemRepositoryContract {
    protected lateinit var fs: FileSystemRepository

    protected abstract fun create(): FileSystemRepository

    /** Creates a file with [content] at [path], with its parent directories. */
    protected abstract fun put(path: String, content: String = "")

    protected abstract fun read(path: String): String?

    @BeforeEach
    fun setUp() {
        fs = create()
    }

    protected fun path(raw: String): FsPath = checkNotNull(FsPath.of(raw).getOrNull())

    protected fun failure(outcome: Outcome<*>): DomainError = (outcome as Outcome.Failure).error

    @Test
    fun `exists tells files, directories and missing paths apart`() = runTest {
        put("a/b.txt", "x")

        assertTrue(fs.exists(path("a/b.txt")))
        assertTrue(fs.exists(path("a")))
        assertFalse(fs.exists(path("a/c.txt")))
    }

    @Test
    fun `createDirectories makes the whole chain and can be repeated`() = runTest {
        assertEquals(Outcome.Success(Unit), fs.createDirectories(path("distros/x/rootfs")))
        assertEquals(Outcome.Success(Unit), fs.createDirectories(path("distros/x/rootfs")))

        assertTrue(fs.exists(path("distros/x")))
        assertTrue(fs.exists(path("distros/x/rootfs")))
    }

    @Test
    fun `createDirectories fails when a file is in the way`() = runTest {
        put("a", "file")

        assertTrue(failure(fs.createDirectories(path("a"))) is DomainError.Io)
    }

    @Test
    fun `deleteRecursively removes a tree and is fine when it is already gone`() = runTest {
        put("d/sub/one.txt", "1")
        put("d/two.txt", "2")
        put("keep.txt", "k")

        assertEquals(Outcome.Success(Unit), fs.deleteRecursively(path("d")))
        assertEquals(Outcome.Success(Unit), fs.deleteRecursively(path("d")))

        assertFalse(fs.exists(path("d")))
        assertFalse(fs.exists(path("d/sub/one.txt")))
        assertTrue(fs.exists(path("keep.txt")))
    }

    @Test
    fun `copyRecursively copies a tree and leaves the source`() = runTest {
        put("src/sub/one.txt", "1")
        put("src/two.txt", "2")

        assertEquals(Outcome.Success(Unit), fs.copyRecursively(path("src"), path("copies/dst")))

        assertEquals("1", read("copies/dst/sub/one.txt"))
        assertEquals("2", read("copies/dst/two.txt"))
        assertEquals("1", read("src/sub/one.txt"))
    }

    @Test
    fun `copyRecursively refuses an existing destination and a missing source`() = runTest {
        put("src/a.txt", "1")
        put("dst/b.txt", "2")

        assertTrue(failure(fs.copyRecursively(path("src"), path("dst"))) is DomainError.Io)
        assertEquals(DomainError.NotFound, failure(fs.copyRecursively(path("nope"), path("x"))))
        assertEquals("2", read("dst/b.txt"))
        assertFalse(fs.exists(path("dst/a.txt")))
    }

    @Test
    fun `move renames a tree and refuses an existing destination`() = runTest {
        put("old/a.txt", "1")
        put("taken/b.txt", "2")

        assertEquals(Outcome.Success(Unit), fs.move(path("old"), path("new/place")))

        assertFalse(fs.exists(path("old")))
        assertEquals("1", read("new/place/a.txt"))
        assertTrue(failure(fs.move(path("new/place"), path("taken"))) is DomainError.Io)
        assertEquals(DomainError.NotFound, failure(fs.move(path("missing"), path("x"))))
        assertTrue(fs.exists(path("new/place")))
    }

    @Test
    fun `sizeOf adds up the files under a path`() = runTest {
        put("d/a.txt", "12345")
        put("d/sub/b.txt", "123")
        put("other.txt", "123456789")

        assertEquals(Outcome.Success(8L), fs.sizeOf(path("d")))
        assertEquals(Outcome.Success(9L), fs.sizeOf(path("other.txt")))
        assertEquals(DomainError.NotFound, failure(fs.sizeOf(path("missing"))))
    }

    @Test
    fun `absolutePathOf puts the relative path under the root`() {
        assertTrue(fs.absolutePathOf(path("distros/a")).endsWith("/distros/a"))
    }
}
