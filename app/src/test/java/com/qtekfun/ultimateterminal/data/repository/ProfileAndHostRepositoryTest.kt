// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.data.local.runDatabaseTest
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SshHost
import java.time.Clock
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileAndHostRepositoryTest {
    private lateinit var db: UltimateTerminalDatabase
    private lateinit var profiles: RoomProfileRepository
    private lateinit var hosts: RoomSshHostRepository
    private lateinit var distros: RoomDistroRepository

    @BeforeEach
    fun setUp() {
        db = inMemoryDatabase()
        profiles = RoomProfileRepository(db.profileDao())
        hosts = RoomSshHostRepository(db.sshHostDao())
        distros = RoomDistroRepository(db.distroDao(), Clock.systemUTC())
    }

    @AfterEach
    fun tearDown() = db.close()

    private fun failure(outcome: Outcome<*>) = (outcome as Outcome.Failure).error

    private suspend fun newDistroId(): Long {
        val directory = checkNotNull(FsPath.of("distros/d").getOrNull())
        return checkNotNull(
            distros.add(NewDistro("d", DistroType.DEBIAN, "12", directory)).getOrNull()
        ).id
    }

    @Test
    fun `a profile is saved with its defaults and read back`() = runDatabaseTest {
        val saved = checkNotNull(profiles.add(Profile(name = " Work ")).getOrNull())

        assertEquals("Work", saved.name)
        assertTrue(saved.id > 0L)
        assertEquals(saved, profiles.get(saved.id))
        assertEquals(listOf(saved), profiles.observeAll().first())
        assertEquals(Profile.DEFAULT_FONT_SIZE, saved.fontSizeSp)
    }

    @Test
    fun `a profile keeps every field`() = runDatabaseTest {
        val distroId = newDistroId()
        val saved = checkNotNull(
            profiles.add(
                Profile(
                    name = "p",
                    colorSchemeId = "dracula",
                    fontFamily = "JetBrains Mono",
                    fontSizeSp = 18,
                    scrollbackLines = 5_000,
                    distroId = distroId,
                    user = "ana",
                    startupCommand = "tmux attach"
                )
            ).getOrNull()
        )

        assertEquals(saved, profiles.get(saved.id))
    }

    @Test
    fun `a profile id given on add is ignored`() = runDatabaseTest {
        val saved = checkNotNull(profiles.add(Profile(id = 77L, name = "p")).getOrNull())

        assertTrue(saved.id != 77L)
    }

    @Test
    fun `profiles check names and limits`() = runDatabaseTest {
        profiles.add(Profile(name = "one"))

        assertEquals(DomainError.NameTaken("ONE"), failure(profiles.add(Profile(name = "ONE"))))
        assertTrue(failure(profiles.add(Profile(name = ""))) is DomainError.InvalidName)
        assertEquals(
            DomainError.InvalidValue("fontSizeSp"),
            failure(profiles.add(Profile(name = "a", fontSizeSp = 5)))
        )
        assertEquals(
            DomainError.InvalidValue("fontSizeSp"),
            failure(profiles.add(Profile(name = "a", fontSizeSp = 73)))
        )
        assertEquals(
            DomainError.InvalidValue("scrollbackLines"),
            failure(profiles.add(Profile(name = "a", scrollbackLines = 99)))
        )
        assertEquals(
            DomainError.InvalidValue("user"),
            failure(profiles.add(Profile(name = "a", user = "-x")))
        )
    }

    @Test
    fun `a profile can be updated and removed`() = runDatabaseTest {
        val one = checkNotNull(profiles.add(Profile(name = "one")).getOrNull())
        profiles.add(Profile(name = "two"))

        assertEquals(
            Outcome.Success(Unit),
            profiles.update(one.copy(name = "uno", fontSizeSp = 20))
        )
        assertEquals(Profile(id = one.id, name = "uno", fontSizeSp = 20), profiles.get(one.id))
        assertEquals(Outcome.Success(Unit), profiles.update(one.copy(name = "UNO")))
        assertEquals(DomainError.NameTaken("two"), failure(profiles.update(one.copy(name = "two"))))
        assertEquals(DomainError.NotFound, failure(profiles.update(one.copy(id = 99L))))

        assertEquals(Outcome.Success(Unit), profiles.remove(one.id))
        assertNull(profiles.get(one.id))
        assertEquals(DomainError.NotFound, failure(profiles.remove(one.id)))
    }

    @Test
    fun `removing a distro clears it from profiles and hosts instead of deleting them`() =
        runDatabaseTest {
            val distroId = newDistroId()
            val profile =
                checkNotNull(profiles.add(Profile(name = "p", distroId = distroId)).getOrNull())
            val host =
                checkNotNull(
                    hosts.add(
                        SshHost(name = "h", host = "h", user = "u", distroId = distroId)
                    ).getOrNull()
                )

            distros.remove(distroId)

            assertNull(profiles.get(profile.id)?.distroId)
            assertNull(hosts.get(host.id)?.distroId)
            assertEquals("p", profiles.get(profile.id)?.name)
        }

    @Test
    fun `a host is saved and read back`() = runDatabaseTest {
        val saved = checkNotNull(
            hosts.add(
                SshHost(
                    name = " prod ",
                    host = " srv.example.com ",
                    port = 2222,
                    user = "ana",
                    keyAlias = "k1"
                )
            )
                .getOrNull()
        )

        assertEquals("prod", saved.name)
        assertEquals("srv.example.com", saved.host)
        assertEquals(saved, hosts.get(saved.id))
        assertEquals(listOf(saved), hosts.observeAll().first())
        assertEquals("k1", saved.keyAlias)
    }

    @Test
    fun `hosts refuse anything that could be read as an ssh option or break the command line`() =
        runDatabaseTest {
            fun host(host: String = "srv", user: String = "ana", port: Int = 22) =
                SshHost(name = "h$host$user$port", host = host, user = user, port = port)

            assertEquals(
                DomainError.InvalidValue("host"),
                failure(hosts.add(host(host = "-oProxyCommand=x")))
            )
            assertEquals(DomainError.InvalidValue("host"), failure(hosts.add(host(host = "a b"))))
            assertEquals(DomainError.InvalidValue("host"), failure(hosts.add(host(host = "a;b"))))
            assertEquals(DomainError.InvalidValue("host"), failure(hosts.add(host(host = ""))))
            assertEquals(DomainError.InvalidValue("user"), failure(hosts.add(host(user = "-l"))))
            assertEquals(DomainError.InvalidValue("user"), failure(hosts.add(host(user = "a b"))))
            assertEquals(DomainError.InvalidValue("port"), failure(hosts.add(host(port = 0))))
            assertEquals(DomainError.InvalidValue("port"), failure(hosts.add(host(port = 65_536))))
            assertTrue(hosts.observeAll().first().isEmpty())
        }

    @Test
    fun `hosts accept names, addresses and ipv6`() = runDatabaseTest {
        listOf(
            "srv.example.com",
            "10.0.0.5",
            "[2001:db8::1]",
            "fe80::1%eth0",
            "my-host_1"
        ).forEach {
            assertTrue(
                hosts.add(SshHost(name = it, host = it, user = "root")) is Outcome.Success,
                it
            )
        }
    }

    @Test
    fun `a host can be updated and removed`() = runDatabaseTest {
        val one = checkNotNull(hosts.add(SshHost(name = "one", host = "a", user = "u")).getOrNull())
        hosts.add(SshHost(name = "two", host = "b", user = "u"))

        assertEquals(Outcome.Success(Unit), hosts.update(one.copy(host = "c", port = 2200)))
        assertEquals(2200, hosts.get(one.id)?.port)
        assertEquals(DomainError.NameTaken("two"), failure(hosts.update(one.copy(name = "two"))))
        assertEquals(DomainError.NotFound, failure(hosts.update(one.copy(id = 99L))))
        assertEquals(DomainError.NameTaken("one"), failure(hosts.add(one.copy(name = "one"))))

        assertEquals(Outcome.Success(Unit), hosts.remove(one.id))
        assertEquals(DomainError.NotFound, failure(hosts.remove(one.id)))
    }
}
