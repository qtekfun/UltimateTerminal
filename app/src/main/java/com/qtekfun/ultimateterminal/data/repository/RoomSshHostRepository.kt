// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.dao.SshHostDao
import com.qtekfun.ultimateterminal.data.local.toDomain
import com.qtekfun.ultimateterminal.data.local.toEntity
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.map
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSshHostRepository @Inject constructor(private val dao: SshHostDao) : SshHostRepository {
    override fun observeAll(): Flow<List<SshHost>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): SshHost? = dao.get(id)?.toDomain()

    override suspend fun add(host: SshHost): Outcome<SshHost> = validate(host).flatMap { valid ->
        val id = dao.insertIfNameFree(valid.copy(id = 0L).toEntity())
        if (id == null) {
            Outcome.Failure(DomainError.NameTaken(valid.name))
        } else {
            Outcome.Success(valid.copy(id = id))
        }
    }

    override suspend fun update(host: SshHost): Outcome<Unit> = validate(host).flatMap { valid ->
        dao.updateIfNameFree(valid.toEntity()).toOutcome(valid.name)
    }

    override suspend fun remove(id: Long): Outcome<Unit> = if (dao.delete(id) > 0) {
        Outcome.Success(Unit)
    } else {
        Outcome.Failure(DomainError.NotFound)
    }

    /** Names, host, user and port are checked so they are safe to put on an `ssh` command line. */
    private fun validate(host: SshHost): Outcome<SshHost> =
        Validation.name(host.name).flatMap { name ->
            Validation.host(host.host).flatMap { hostName ->
                Validation.user(host.user).flatMap { user ->
                    Validation.port(host.port).map {
                        host.copy(name = name, host = hostName, user = user)
                    }
                }
            }
        }
}
