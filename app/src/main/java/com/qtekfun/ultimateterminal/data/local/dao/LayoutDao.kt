// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.qtekfun.ultimateterminal.data.local.entity.LayoutEntity
import com.qtekfun.ultimateterminal.data.local.entity.WriteResult
import kotlinx.coroutines.flow.Flow

@Dao
interface LayoutDao {
    @Insert
    suspend fun insert(layout: LayoutEntity): Long

    @Update
    suspend fun update(layout: LayoutEntity): Int

    @Query("SELECT * FROM layout ORDER BY name")
    fun observeAll(): Flow<List<LayoutEntity>>

    @Query("SELECT * FROM layout WHERE id = :id")
    suspend fun get(id: Long): LayoutEntity?

    @Query("SELECT COUNT(*) FROM layout WHERE name = :name AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int

    @Query("DELETE FROM layout WHERE id = :id")
    suspend fun delete(id: Long): Int

    /** Inserts [layout], or returns null if its name is taken. */
    @Transaction
    suspend fun insertIfNameFree(layout: LayoutEntity): Long? =
        if (countByName(layout.name, exceptId = 0L) > 0) null else insert(layout)

    @Transaction
    suspend fun updateIfNameFree(layout: LayoutEntity): WriteResult = when {
        get(layout.id) == null -> WriteResult.NOT_FOUND

        countByName(layout.name, exceptId = layout.id) > 0 -> WriteResult.NAME_TAKEN

        else -> {
            update(layout)
            WriteResult.OK
        }
    }
}
