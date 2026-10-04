// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import androidx.sqlite.SQLiteException
import com.qtekfun.ultimateterminal.data.local.dao.DistroDao
import com.qtekfun.ultimateterminal.data.local.entity.DistroEntity
import com.qtekfun.ultimateterminal.data.local.entity.WriteResult
import com.qtekfun.ultimateterminal.data.local.toDomainOrNull
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.map
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDistroRepository @Inject constructor(
    private val dao: DistroDao,
    private val clock: Clock
) : DistroRepository {
    override fun observeAll(): Flow<List<Distro>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun get(id: Long): Distro? = dao.get(id)?.toDomainOrNull()

    override suspend fun getDefault(): Distro? = dao.getDefault()?.toDomainOrNull()

    override suspend fun add(distro: NewDistro): Outcome<Distro> =
        Validation.name(distro.name).flatMap { name ->
            Validation.user(distro.defaultUser).flatMap { user ->
                insert(
                    DistroEntity(
                        name = name,
                        type = distro.type,
                        release = distro.release,
                        directory = distro.directory.value,
                        defaultUser = user,
                        state = DistroState.INSTALLING,
                        sizeBytes = 0L,
                        installedAtMillis = clock.millis(),
                        isDefault = false
                    )
                )
            }
        }

    private suspend fun insert(entity: DistroEntity): Outcome<Distro> {
        val inserted: Outcome<Long?> = try {
            Outcome.Success(dao.insertIfNameFree(entity))
        } catch (_: SQLiteException) {
            // The directory is already used by another distro.
            Outcome.Failure(DomainError.Io("directory already registered"))
        }
        return inserted.flatMap { id ->
            if (id == null) {
                Outcome.Failure(DomainError.NameTaken(entity.name))
            } else {
                Outcome.Success(checkNotNull(dao.get(id)?.toDomainOrNull()))
            }
        }
    }

    override suspend fun rename(id: Long, name: String): Outcome<Unit> =
        Validation.name(name).flatMap { valid -> dao.rename(id, valid).toOutcome(valid) }

    override suspend fun setDefaultUser(id: Long, user: String): Outcome<Unit> =
        Validation.user(user).flatMap { valid ->
            if (dao.updateDefaultUser(id, valid) > 0) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(DomainError.NotFound)
            }
        }

    override suspend fun updateState(
        id: Long,
        state: DistroState,
        sizeBytes: Long?
    ): Outcome<Unit> = if (dao.updateState(id, state, sizeBytes) > 0) {
        Outcome.Success(Unit)
    } else {
        Outcome.Failure(DomainError.NotFound)
    }

    override suspend fun setDefault(id: Long): Outcome<Unit> =
        dao.setDefaultExclusive(id).toOutcome()

    override suspend fun remove(id: Long): Outcome<Unit> = dao.deleteAndPromote(id).toOutcome()
}

/** Maps a DAO write result to an [Outcome]; [name] is reported when the name was taken. */
internal fun WriteResult.toOutcome(name: String = ""): Outcome<Unit> = when (this) {
    WriteResult.OK -> Outcome.Success(Unit)
    WriteResult.NOT_FOUND -> Outcome.Failure(DomainError.NotFound)
    WriteResult.NAME_TAKEN -> Outcome.Failure(DomainError.NameTaken(name))
}
