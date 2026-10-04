// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.dao.ProfileDao
import com.qtekfun.ultimateterminal.data.local.toDomain
import com.qtekfun.ultimateterminal.data.local.toEntity
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProfileRepository @Inject constructor(private val dao: ProfileDao) : ProfileRepository {
    override fun observeAll(): Flow<List<Profile>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): Profile? = dao.get(id)?.toDomain()

    override suspend fun add(profile: Profile): Outcome<Profile> =
        validate(profile).flatMap { valid ->
            val id = dao.insertIfNameFree(valid.copy(id = 0L).toEntity())
            if (id == null) {
                Outcome.Failure(DomainError.NameTaken(valid.name))
            } else {
                Outcome.Success(valid.copy(id = id))
            }
        }

    override suspend fun update(profile: Profile): Outcome<Unit> =
        validate(profile).flatMap { valid ->
            dao.updateIfNameFree(valid.toEntity()).toOutcome(valid.name)
        }

    override suspend fun remove(id: Long): Outcome<Unit> = if (dao.delete(id) > 0) {
        Outcome.Success(Unit)
    } else {
        Outcome.Failure(DomainError.NotFound)
    }

    private fun validate(profile: Profile): Outcome<Profile> =
        Validation.name(profile.name).flatMap { name ->
            when {
                profile.fontSizeSp !in Profile.FONT_SIZE_RANGE ->
                    Outcome.Failure(DomainError.InvalidValue("fontSizeSp"))

                profile.scrollbackLines !in Profile.SCROLLBACK_RANGE ->
                    Outcome.Failure(DomainError.InvalidValue("scrollbackLines"))

                profile.user != null && Validation.user(profile.user) is Outcome.Failure ->
                    Outcome.Failure(DomainError.InvalidValue("user"))

                else -> Outcome.Success(profile.copy(name = name))
            }
        }
}
