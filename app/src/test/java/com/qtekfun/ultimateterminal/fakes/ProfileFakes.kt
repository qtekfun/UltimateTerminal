// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.fakes

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Profiles held in memory; names are unique ignoring case, as in the real repository. */
class FakeProfileRepository(initial: List<Profile> = emptyList()) : ProfileRepository {
    private val items = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<Profile>> = items

    override suspend fun get(id: Long): Profile? = items.value.firstOrNull { it.id == id }

    override suspend fun add(profile: Profile): Outcome<Profile> =
        if (items.value.any { it.name.equals(profile.name, ignoreCase = true) }) {
            Outcome.Failure(DomainError.NameTaken(profile.name))
        } else {
            val created = profile.copy(id = nextId++)
            items.value += created
            Outcome.Success(created)
        }

    override suspend fun update(profile: Profile): Outcome<Unit> {
        val taken = items.value.any {
            it.id != profile.id && it.name.equals(profile.name, ignoreCase = true)
        }
        return when {
            taken -> Outcome.Failure(DomainError.NameTaken(profile.name))

            items.value.none { it.id == profile.id } -> Outcome.Failure(DomainError.NotFound)

            else -> {
                items.value = items.value.map { if (it.id == profile.id) profile else it }
                Outcome.Success(Unit)
            }
        }
    }

    override suspend fun remove(id: Long): Outcome<Unit> {
        val found = items.value.any { it.id == id }
        items.value = items.value.filterNot { it.id == id }
        return if (found) Outcome.Success(Unit) else Outcome.Failure(DomainError.NotFound)
    }
}

/** Layouts held in memory. */
class FakeLayoutRepository(initial: List<Layout> = emptyList()) : LayoutRepository {
    private val items = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<Layout>> = items

    override suspend fun get(id: Long): Layout? = items.value.firstOrNull { it.id == id }

    override suspend fun add(layout: Layout): Outcome<Layout> {
        val created = layout.copy(id = nextId++)
        items.value += created
        return Outcome.Success(created)
    }

    override suspend fun update(layout: Layout): Outcome<Unit> {
        items.value = items.value.map { if (it.id == layout.id) layout else it }
        return Outcome.Success(Unit)
    }

    override suspend fun remove(id: Long): Outcome<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return Outcome.Success(Unit)
    }
}

/** A fixed list of distros: only what the code under test reads is implemented. */
class StaticDistros(private val distros: List<Distro>) : DistroRepository {
    override fun observeAll(): Flow<List<Distro>> = MutableStateFlow(distros)

    override suspend fun get(id: Long): Distro? = distros.firstOrNull { it.id == id }

    override suspend fun getDefault(): Distro? = distros.firstOrNull { it.isDefault }

    override suspend fun add(distro: NewDistro): Outcome<Distro> = unused()

    override suspend fun rename(id: Long, name: String): Outcome<Unit> = unused()

    override suspend fun setDefaultUser(id: Long, user: String): Outcome<Unit> = unused()

    override suspend fun updateState(
        id: Long,
        state: DistroState,
        sizeBytes: Long?
    ): Outcome<Unit> = unused()

    override suspend fun setDefault(id: Long): Outcome<Unit> = unused()

    override suspend fun remove(id: Long): Outcome<Unit> = unused()

    private fun unused(): Nothing = error("not needed by these tests")
}
