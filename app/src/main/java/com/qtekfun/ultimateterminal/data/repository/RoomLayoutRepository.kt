// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.dao.LayoutDao
import com.qtekfun.ultimateterminal.data.local.toDomainOrNull
import com.qtekfun.ultimateterminal.data.local.toEntity
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLayoutRepository @Inject constructor(private val dao: LayoutDao) : LayoutRepository {
    override fun observeAll(): Flow<List<Layout>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun get(id: Long): Layout? = dao.get(id)?.toDomainOrNull()

    override suspend fun add(layout: Layout): Outcome<Layout> =
        Validation.name(layout.name).flatMap { name ->
            val id = dao.insertIfNameFree(layout.copy(id = 0L, name = name).toEntity())
            if (id == null) {
                Outcome.Failure(DomainError.NameTaken(name))
            } else {
                Outcome.Success(layout.copy(id = id, name = name))
            }
        }

    override suspend fun update(layout: Layout): Outcome<Unit> =
        Validation.name(layout.name).flatMap { name ->
            dao.updateIfNameFree(layout.copy(name = name).toEntity()).toOutcome(name)
        }

    override suspend fun remove(id: Long): Outcome<Unit> = if (dao.delete(id) > 0) {
        Outcome.Success(Unit)
    } else {
        Outcome.Failure(DomainError.NotFound)
    }
}
