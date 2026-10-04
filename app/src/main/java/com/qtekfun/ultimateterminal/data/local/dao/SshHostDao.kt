// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.qtekfun.ultimateterminal.data.local.entity.SshHostEntity
import com.qtekfun.ultimateterminal.data.local.entity.WriteResult
import kotlinx.coroutines.flow.Flow

@Dao
interface SshHostDao {
    @Insert
    suspend fun insert(host: SshHostEntity): Long

    @Update
    suspend fun update(host: SshHostEntity): Int

    @Query("SELECT * FROM ssh_host ORDER BY name")
    fun observeAll(): Flow<List<SshHostEntity>>

    @Query("SELECT * FROM ssh_host WHERE id = :id")
    suspend fun get(id: Long): SshHostEntity?

    @Query("SELECT COUNT(*) FROM ssh_host WHERE name = :name AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int

    @Query("DELETE FROM ssh_host WHERE id = :id")
    suspend fun delete(id: Long): Int

    /** Inserts [host], or returns null if its name is taken. */
    @Transaction
    suspend fun insertIfNameFree(host: SshHostEntity): Long? =
        if (countByName(host.name, exceptId = 0L) > 0) null else insert(host)

    @Transaction
    suspend fun updateIfNameFree(host: SshHostEntity): WriteResult = when {
        get(host.id) == null -> WriteResult.NOT_FOUND

        countByName(host.name, exceptId = host.id) > 0 -> WriteResult.NAME_TAKEN

        else -> {
            update(host)
            WriteResult.OK
        }
    }
}
