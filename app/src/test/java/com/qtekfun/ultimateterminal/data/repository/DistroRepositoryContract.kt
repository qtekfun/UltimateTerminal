// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.runDatabaseTest
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * What every [DistroRepository] must do. It runs against the Room implementation and against the
 * in-memory fake, so the fake used in other tests behaves like the real thing.
 */
abstract class DistroRepositoryContract {
    protected lateinit var repo: DistroRepository

    protected abstract fun create(): DistroRepository

    protected open fun close() = Unit

    @BeforeEach
    fun setUp() {
        repo = create()
    }

    @AfterEach
    fun tearDown() = close()

    protected fun path(raw: String): FsPath = checkNotNull(FsPath.of(raw).getOrNull())

    private fun draft(name: String, dir: String = "distros/$name") =
        NewDistro(name = name, type = DistroType.DEBIAN, release = "12", directory = path(dir))

    private suspend fun add(name: String): Distro = checkNotNull(repo.add(draft(name)).getOrNull())

    private fun failure(outcome: Outcome<*>): DomainError = (outcome as Outcome.Failure).error

    @Test
    fun `the first distro becomes the default and later ones do not`() = runDatabaseTest {
        val first = add("debian")
        val second = add("ubuntu")

        assertTrue(first.isDefault)
        assertFalse(second.isDefault)
        assertEquals(DistroState.INSTALLING, first.state)
        assertEquals("root", first.defaultUser)
        assertEquals(first, repo.getDefault())
    }

    @Test
    fun `add trims the name and keeps what was asked`() = runDatabaseTest {
        val distro = checkNotNull(
            repo.add(
                NewDistro(
                    "  work  ",
                    DistroType.ALPINE,
                    "3.20",
                    path("distros/a"),
                    defaultUser = "ana"
                )
            ).getOrNull()
        )

        assertEquals("work", distro.name)
        assertEquals(DistroType.ALPINE, distro.type)
        assertEquals("3.20", distro.release)
        assertEquals("distros/a", distro.directory.value)
        assertEquals("ana", distro.defaultUser)
        assertEquals(distro, repo.get(distro.id))
    }

    @Test
    fun `add rejects bad names and bad users`() = runDatabaseTest {
        assertTrue(failure(repo.add(draft("   "))) is DomainError.InvalidName)
        assertTrue(failure(repo.add(draft("a".repeat(65)))) is DomainError.InvalidName)
        assertTrue(failure(repo.add(draft("bad\nname"))) is DomainError.InvalidName)
        assertEquals(
            DomainError.InvalidValue("user"),
            failure(repo.add(draft("x").copy(defaultUser = "-root")))
        )
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun `names are unique ignoring case`() = runDatabaseTest {
        add("Debian")

        assertEquals(
            DomainError.NameTaken("debian"),
            failure(repo.add(draft("debian", "distros/other")))
        )
    }

    @Test
    fun `a directory cannot be registered twice`() = runDatabaseTest {
        add("one")

        assertTrue(failure(repo.add(draft("two", "distros/one"))) is DomainError.Io)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `distros are listed by name ignoring case`() = runDatabaseTest {
        add("beta")
        add("Alpha")
        add("gamma")

        assertEquals(listOf("Alpha", "beta", "gamma"), repo.observeAll().first().map { it.name })
    }

    @Test
    fun `rename changes the name and checks it`() = runDatabaseTest {
        val one = add("one")
        add("two")

        assertEquals(Outcome.Success(Unit), repo.rename(one.id, "uno"))
        assertEquals("uno", repo.get(one.id)?.name)
        assertEquals(DomainError.NameTaken("TWO"), failure(repo.rename(one.id, "TWO")))
        assertTrue(failure(repo.rename(one.id, " ")) is DomainError.InvalidName)
        assertEquals(DomainError.NotFound, failure(repo.rename(999L, "x")))
    }

    @Test
    fun `renaming a distro to its own name with another case is allowed`() = runDatabaseTest {
        val one = add("one")

        assertEquals(Outcome.Success(Unit), repo.rename(one.id, "ONE"))
        assertEquals("ONE", repo.get(one.id)?.name)
    }

    @Test
    fun `setDefault leaves exactly one default`() = runDatabaseTest {
        add("one")
        val two = add("two")
        val three = add("three")

        assertEquals(Outcome.Success(Unit), repo.setDefault(three.id))

        assertEquals(
            listOf(three.id),
            repo.observeAll().first().filter {
                it.isDefault
            }.map { it.id }
        )
        assertEquals(two.id, repo.get(two.id)?.id)
        assertEquals(DomainError.NotFound, failure(repo.setDefault(999L)))
        assertEquals(three.id, repo.getDefault()?.id)
    }

    @Test
    fun `removing the default promotes the oldest remaining distro`() = runDatabaseTest {
        val one = add("one")
        val two = add("two")
        val three = add("three")

        assertEquals(Outcome.Success(Unit), repo.remove(one.id))

        assertNull(repo.get(one.id))
        assertEquals(two.id, repo.getDefault()?.id)
        assertEquals(1, repo.observeAll().first().count { it.isDefault })
        assertFalse(repo.get(three.id)!!.isDefault)
    }

    @Test
    fun `removing a distro that is not the default keeps the default`() = runDatabaseTest {
        val one = add("one")
        val two = add("two")

        repo.remove(two.id)

        assertEquals(one.id, repo.getDefault()?.id)
    }

    @Test
    fun `removing the last distro leaves no default`() = runDatabaseTest {
        val one = add("one")

        repo.remove(one.id)

        assertNull(repo.getDefault())
        assertEquals(DomainError.NotFound, failure(repo.remove(one.id)))
    }

    @Test
    fun `updateState records progress and keeps the size when none is given`() = runDatabaseTest {
        val one = add("one")

        repo.updateState(one.id, DistroState.INSTALLING, sizeBytes = 1_234L)
        repo.updateState(one.id, DistroState.READY)

        val stored = checkNotNull(repo.get(one.id))
        assertEquals(DistroState.READY, stored.state)
        assertEquals(1_234L, stored.sizeBytes)
        assertEquals(DomainError.NotFound, failure(repo.updateState(999L, DistroState.FAILED)))
    }

    @Test
    fun `setDefaultUser checks the user`() = runDatabaseTest {
        val one = add("one")

        assertEquals(Outcome.Success(Unit), repo.setDefaultUser(one.id, "ana"))
        assertEquals("ana", repo.get(one.id)?.defaultUser)
        assertEquals(DomainError.InvalidValue("user"), failure(repo.setDefaultUser(one.id, "a b")))
        assertEquals(DomainError.NotFound, failure(repo.setDefaultUser(999L, "ana")))
    }

    @Test
    fun `get returns null for unknown ids`() = runDatabaseTest {
        assertNull(repo.get(1L))
        assertNull(repo.getDefault())
    }
}
