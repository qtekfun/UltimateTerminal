// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.qtekfun.ultimateterminal.data.local.entity.DistroEntity
import com.qtekfun.ultimateterminal.data.local.entity.WriteResult
import com.qtekfun.ultimateterminal.domain.model.DistroState
import kotlinx.coroutines.flow.Flow

/** The reading queries of [DistroDao]. */
interface DistroReads {
    @Query("SELECT * FROM distro ORDER BY name")
    fun observeAll(): Flow<List<DistroEntity>>

    @Query("SELECT * FROM distro WHERE id = :id")
    suspend fun get(id: Long): DistroEntity?

    @Query("SELECT * FROM distro WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): DistroEntity?

    @Query("SELECT COUNT(*) FROM distro")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM distro WHERE name = :name AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int

    @Query("SELECT id FROM distro ORDER BY installedAtMillis, id LIMIT 1")
    suspend fun oldestId(): Long?
}

/** The single-statement writes of [DistroDao]. */
interface DistroWrites {
    @Insert
    suspend fun insert(distro: DistroEntity): Long

    @Query("UPDATE distro SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String): Int

    @Query("UPDATE distro SET defaultUser = :user WHERE id = :id")
    suspend fun updateDefaultUser(id: Long, user: String): Int

    @Query(
        "UPDATE distro SET state = :state, sizeBytes = COALESCE(:sizeBytes, sizeBytes) WHERE id = :id"
    )
    suspend fun updateState(id: Long, state: DistroState, sizeBytes: Long?): Int

    @Query("UPDATE distro SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefault()

    @Query("UPDATE distro SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: Long): Int

    @Query("DELETE FROM distro WHERE id = :id")
    suspend fun delete(id: Long): Int
}

/** Distro rows, plus the operations that must read and write in one transaction. */
@Dao
interface DistroDao :
    DistroReads,
    DistroWrites {
    /** Inserts [distro], or returns null if its name is taken. The first distro becomes the default. */
    @Transaction
    suspend fun insertIfNameFree(distro: DistroEntity): Long? {
        if (countByName(distro.name, exceptId = 0L) > 0) return null
        return insert(distro.copy(isDefault = count() == 0))
    }

    @Transaction
    suspend fun rename(id: Long, name: String): WriteResult = when {
        get(id) == null -> WriteResult.NOT_FOUND

        countByName(name, exceptId = id) > 0 -> WriteResult.NAME_TAKEN

        else -> {
            updateName(id, name)
            WriteResult.OK
        }
    }

    /** Makes [id] the only default distro. */
    @Transaction
    suspend fun setDefaultExclusive(id: Long): WriteResult {
        if (get(id) == null) return WriteResult.NOT_FOUND
        clearDefault()
        markDefault(id)
        return WriteResult.OK
    }

    /** Deletes [id]; if it was the default, the oldest remaining distro takes over. */
    @Transaction
    suspend fun deleteAndPromote(id: Long): WriteResult {
        val distro = get(id) ?: return WriteResult.NOT_FOUND
        delete(id)
        if (distro.isDefault) oldestId()?.let { markDefault(it) }
        return WriteResult.OK
    }
}
