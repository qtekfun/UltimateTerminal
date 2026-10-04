// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.DistroEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.fakes.FakeDistroRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomDistroRepositoryTest : DistroRepositoryContract() {
    private lateinit var db: UltimateTerminalDatabase

    override fun create(): DistroRepository {
        db = inMemoryDatabase()
        return RoomDistroRepository(db.distroDao(), FIXED_CLOCK)
    }

    override fun close() = db.close()

    @Test
    fun `installedAt comes from the clock`() = runTest {
        val distro = repo.add(NewDistro("x", DistroType.UBUNTU, "24.04", path("distros/x")))

        assertEquals(FIXED_CLOCK.instant(), (distro as Outcome.Success).value.installedAt)
    }

    @Test
    fun `a stored row with a directory outside the storage is never returned`() = runTest {
        // Such a row could come from a tampered backup; the path must not reach the file system.
        val id = db.distroDao().insert(
            DistroEntity(
                name = "evil",
                type = DistroType.DEBIAN,
                release = "12",
                directory = "../../etc",
                defaultUser = "root",
                state = DistroState.READY,
                sizeBytes = 0L,
                installedAtMillis = 0L,
                isDefault = false
            )
        )

        assertEquals(emptyList<Any>(), repo.observeAll().first())
        assertEquals(null, repo.get(id))
    }
}

class FakeDistroRepositoryTest : DistroRepositoryContract() {
    override fun create(): DistroRepository = FakeDistroRepository(FIXED_CLOCK)
}

private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
