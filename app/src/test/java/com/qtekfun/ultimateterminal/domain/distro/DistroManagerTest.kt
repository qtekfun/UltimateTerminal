// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.fakes.FailingFileSystem
import com.qtekfun.ultimateterminal.fakes.FakeDistroRepository
import com.qtekfun.ultimateterminal.fakes.FlakyDistroRepository
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DistroManagerTest {
    private val storage = InMemoryFileSystemRepository()
    private val repository = FakeDistroRepository()
    private var tokens = 0
    private val manager = DistroManager(repository, storage) { "copy${++tokens}" }

    private fun path(raw: String): FsPath = (FsPath.of(raw) as Outcome.Success).value

    /** A finished distro with one file, as an install leaves it. */
    private suspend fun readyDistro(name: String, directory: String): Distro {
        val added = repository.add(
            NewDistro(name, DistroType.ALPINE, "3.22", path(directory), "alice")
        ) as Outcome.Success
        storage.putFile("$directory/etc/os-release", "NAME=$name".toByteArray())
        repository.updateState(added.value.id, DistroState.READY, 10L)
        return checkNotNull(repository.get(added.value.id))
    }

    private suspend fun names() = repository.observeAll().first().map { it.name }

    @Test
    fun observeListsTheInstalledDistros() = runTest {
        readyDistro("Alpine", "distros/a")

        assertEquals(listOf("Alpine"), manager.observe().first().map { it.name })
    }

    @Test
    fun renamingChecksUniquenessAndKeepsTheFiles() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")
        readyDistro("Debian", "distros/d")

        assertEquals(
            Outcome.Failure(DomainError.NameTaken("debian")),
            manager.rename(alpine.id, "debian")
        )
        assertEquals(Outcome.Success(Unit), manager.rename(alpine.id, "Work"))

        assertEquals(listOf("Debian", "Work"), names())
        assertTrue(storage.exists(path("distros/a/etc/os-release")))
    }

    @Test
    fun theDefaultUserCanBeChanged() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")

        assertEquals(Outcome.Success(Unit), manager.setDefaultUser(alpine.id, "bob"))

        assertEquals("bob", repository.get(alpine.id)?.defaultUser)
    }

    @Test
    fun theDefaultUserMustBeAValidGuestUserAndRootIsAllowed() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")

        assertEquals(Outcome.Success(Unit), manager.setDefaultUser(alpine.id, " root "))
        assertEquals("root", repository.get(alpine.id)?.defaultUser)
        listOf("", "Bob", "-x", "a b", "1abc", "a".repeat(33)).forEach {
            assertEquals(
                Outcome.Failure(DomainError.InvalidValue("user")),
                manager.setDefaultUser(alpine.id, it)
            )
        }
        assertEquals("root", repository.get(alpine.id)?.defaultUser)
        assertEquals(
            Outcome.Failure(DomainError.NotFound),
            manager.setDefaultUser(999L, "bob")
        )
    }

    @Test
    fun onlyAFinishedDistroCanBeTheDefault() = runTest {
        readyDistro("Alpine", "distros/a")
        val debian = readyDistro("Debian", "distros/d")
        val installing = repository.add(
            NewDistro("Ubuntu", DistroType.UBUNTU, "24.04", path("distros/u"))
        ) as Outcome.Success

        assertEquals(Outcome.Success(Unit), manager.setDefault(debian.id))
        assertEquals(debian.id, repository.getDefault()?.id)
        assertEquals(
            Outcome.Failure(DomainError.InvalidValue("state")),
            manager.setDefault(installing.value.id)
        )
        assertEquals(Outcome.Failure(DomainError.NotFound), manager.setDefault(999))
        assertEquals(debian.id, repository.getDefault()?.id)
    }

    @Test
    fun aCopyHasItsOwnFilesAndIsNotTheDefault() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")

        val copy = (manager.duplicate(alpine.id, "Alpine copy") as Outcome.Success).value

        assertEquals(DistroState.READY, copy.state)
        assertEquals("distros/copy1", copy.directory.value)
        assertEquals(alpine.type, copy.type)
        assertEquals(alpine.defaultUser, copy.defaultUser)
        assertEquals("NAME=Alpine".length.toLong(), copy.sizeBytes)
        assertFalse(copy.isDefault)
        assertTrue(alpine.isDefault)
        assertEquals("NAME=Alpine", String(storage.readFile("distros/copy1/etc/os-release")!!))
        // The original is untouched.
        assertTrue(storage.exists(path("distros/a/etc/os-release")))
    }

    @Test
    fun aCopyWithATakenNameCopiesNothing() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")

        val result = manager.duplicate(alpine.id, "alpine")

        assertEquals(Outcome.Failure(DomainError.NameTaken("alpine")), result)
        assertFalse(storage.exists(path("distros/copy1")))
        assertEquals(1, names().size)
    }

    @Test
    fun onlyAFinishedDistroCanBeCopied() = runTest {
        val installing = repository.add(
            NewDistro("Ubuntu", DistroType.UBUNTU, "24.04", path("distros/u"))
        ) as Outcome.Success

        assertEquals(
            Outcome.Failure(DomainError.InvalidValue("state")),
            manager.duplicate(installing.value.id, "Ubuntu copy")
        )
        assertEquals(Outcome.Failure(DomainError.NotFound), manager.duplicate(999, "x"))
    }

    @Test
    fun aFailedCopyLeavesNeitherARowNorFiles() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")
        val failing = DistroManager(repository, FailingFileSystem(storage, failCopy = true)) {
            "copy9"
        }

        val result = failing.duplicate(alpine.id, "Alpine copy")

        assertInstanceOf(Outcome.Failure::class.java, result)
        assertEquals(listOf("Alpine"), names())
        assertFalse(storage.exists(path("distros/copy9")))
    }

    @Test
    fun deletingRemovesTheFilesAndTheRow() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")
        val debian = readyDistro("Debian", "distros/d")

        assertEquals(Outcome.Success(Unit), manager.delete(alpine.id))

        assertFalse(storage.exists(path("distros/a")))
        assertNull(repository.get(alpine.id))
        // The default moved to the remaining distro.
        assertEquals(debian.id, repository.getDefault()?.id)
    }

    @Test
    fun deletingSomethingUnknownOrStillInstallingIsRefused() = runTest {
        val installing = repository.add(
            NewDistro("Ubuntu", DistroType.UBUNTU, "24.04", path("distros/u"))
        ) as Outcome.Success

        assertEquals(Outcome.Failure(DomainError.NotFound), manager.delete(999))
        assertEquals(
            Outcome.Failure(DomainError.InvalidValue("state")),
            manager.delete(installing.value.id)
        )
        assertEquals(1, names().size)
    }

    @Test
    fun anInterruptedDeletionShowsAsBrokenAndCanBeRetried() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")
        val failing = DistroManager(repository, FailingFileSystem(storage, failDelete = true))

        val result = failing.delete(alpine.id)

        assertInstanceOf(Outcome.Failure::class.java, result)
        // The row says it is broken instead of looking intact.
        assertEquals(DistroState.FAILED, repository.get(alpine.id)?.state)
        assertEquals(Outcome.Success(Unit), manager.delete(alpine.id))
        assertEquals(emptyList<String>(), names())
    }

    @Test
    fun recoveryRemovesWhatAnInterruptedInstallLeftBehind() = runTest {
        val ready = readyDistro("Alpine", "distros/a")
        repository.add(NewDistro("Debian", DistroType.DEBIAN, "13", path("distros/d")))
        storage.putFile("distros/d/etc/os-release")
        val broken = repository.add(
            NewDistro("Ubuntu", DistroType.UBUNTU, "24.04", path("distros/u"))
        ) as Outcome.Success
        repository.updateState(broken.value.id, DistroState.FAILED)
        storage.putFile("distros-tmp/token/archive")

        val cleaned = manager.recoverInterrupted()

        assertEquals(2, cleaned)
        assertEquals(listOf("Alpine"), names())
        assertFalse(storage.exists(path("distros/d")))
        assertFalse(storage.exists(path("distros-tmp")))
        // A finished distro is never touched.
        assertTrue(storage.exists(path("distros/a/etc/os-release")))
        assertEquals(ready.id, repository.getDefault()?.id)
        assertEquals(0, manager.recoverInterrupted())
    }

    @Test
    fun aCopyThatCannotBeReadBackIsRemoved() = runTest {
        val alpine = readyDistro("Alpine", "distros/a")
        val flaky = FlakyDistroRepository(
            repository,
            hideAfterReady = true,
            untouched = setOf(alpine.id)
        )
        val manager = DistroManager(flaky, storage) { "copy7" }

        val result = manager.duplicate(alpine.id, "Alpine copy")

        // The source is found, the copy is made, but the new row cannot be read back: nothing of
        // the copy may stay behind, neither its files nor its registration.
        assertEquals(Outcome.Failure(DomainError.NotFound), result)
        assertFalse(storage.exists(path("distros/copy7")))
        assertEquals(listOf("Alpine"), names())
    }
}
