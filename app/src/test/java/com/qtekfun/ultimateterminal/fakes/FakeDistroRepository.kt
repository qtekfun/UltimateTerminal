// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.fakes

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DistroRepository] for tests of code that depends on it. It is checked against the same
 * contract as the Room implementation (DistroRepositoryContract), so both behave alike.
 */
class FakeDistroRepository(private val clock: Clock = Clock.systemUTC()) : DistroRepository {
    private val distros = MutableStateFlow<List<Distro>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Distro>> =
        distros.map { list -> list.sortedBy { it.name.lowercase() } }

    override suspend fun get(id: Long): Distro? = distros.value.firstOrNull { it.id == id }

    override suspend fun getDefault(): Distro? = distros.value.firstOrNull { it.isDefault }

    override suspend fun add(distro: NewDistro): Outcome<Distro> =
        Validation.name(distro.name).flatMap { name ->
            Validation.user(distro.defaultUser).flatMap { user ->
                when {
                    isNameTaken(name, exceptId = 0L) -> Outcome.Failure(DomainError.NameTaken(name))

                    distros.value.any { it.directory == distro.directory } ->
                        Outcome.Failure(DomainError.Io("directory already registered"))

                    else -> {
                        val created = Distro(
                            id = nextId++,
                            name = name,
                            type = distro.type,
                            release = distro.release,
                            directory = distro.directory,
                            defaultUser = user,
                            state = DistroState.INSTALLING,
                            sizeBytes = 0L,
                            installedAt = clock.instant(),
                            isDefault = distros.value.isEmpty()
                        )
                        distros.value += created
                        Outcome.Success(created)
                    }
                }
            }
        }

    override suspend fun rename(id: Long, name: String): Outcome<Unit> =
        Validation.name(name).flatMap { valid ->
            when {
                get(id) == null -> Outcome.Failure(DomainError.NotFound)
                isNameTaken(valid, exceptId = id) -> Outcome.Failure(DomainError.NameTaken(valid))
                else -> modify(id) { it.copy(name = valid) }
            }
        }

    override suspend fun setDefaultUser(id: Long, user: String): Outcome<Unit> =
        Validation.user(user).flatMap { valid -> modify(id) { it.copy(defaultUser = valid) } }

    override suspend fun updateState(
        id: Long,
        state: DistroState,
        sizeBytes: Long?
    ): Outcome<Unit> = modify(id) { it.copy(state = state, sizeBytes = sizeBytes ?: it.sizeBytes) }

    override suspend fun setDefault(id: Long): Outcome<Unit> {
        if (get(id) == null) return Outcome.Failure(DomainError.NotFound)
        distros.value = distros.value.map { it.copy(isDefault = it.id == id) }
        return Outcome.Success(Unit)
    }

    override suspend fun remove(id: Long): Outcome<Unit> {
        val removed = get(id) ?: return Outcome.Failure(DomainError.NotFound)
        var remaining = distros.value.filter { it.id != id }
        if (removed.isDefault) {
            val oldest = remaining.minWithOrNull(compareBy({ it.installedAt }, { it.id }))
            remaining = remaining.map { it.copy(isDefault = it.id == oldest?.id) }
        }
        distros.value = remaining
        return Outcome.Success(Unit)
    }

    private fun isNameTaken(name: String, exceptId: Long) =
        distros.value.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    private fun modify(id: Long, change: (Distro) -> Distro): Outcome<Unit> {
        if (distros.value.none { it.id == id }) return Outcome.Failure(DomainError.NotFound)
        distros.value = distros.value.map { if (it.id == id) change(it) else it }
        return Outcome.Success(Unit)
    }
}
