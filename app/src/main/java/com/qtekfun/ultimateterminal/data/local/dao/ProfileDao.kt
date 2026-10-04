// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.qtekfun.ultimateterminal.data.local.entity.ProfileEntity
import com.qtekfun.ultimateterminal.data.local.entity.WriteResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity): Int

    @Query("SELECT * FROM profile ORDER BY name")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile WHERE name = :name AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun delete(id: Long): Int

    /** Inserts [profile], or returns null if its name is taken. */
    @Transaction
    suspend fun insertIfNameFree(profile: ProfileEntity): Long? =
        if (countByName(profile.name, exceptId = 0L) > 0) null else insert(profile)

    @Transaction
    suspend fun updateIfNameFree(profile: ProfileEntity): WriteResult = when {
        get(profile.id) == null -> WriteResult.NOT_FOUND

        countByName(profile.name, exceptId = profile.id) > 0 -> WriteResult.NAME_TAKEN

        else -> {
            update(profile)
            WriteResult.OK
        }
    }
}
