// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType

/** Outcome of a DAO write that checks a name or a row first, inside one transaction. */
enum class WriteResult { OK, NOT_FOUND, NAME_TAKEN }

@Entity(
    tableName = "distro",
    indices = [Index("name", unique = true), Index("directory", unique = true)]
)
data class DistroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val type: DistroType,
    val release: String,
    val directory: String,
    val defaultUser: String,
    val state: DistroState,
    val sizeBytes: Long,
    val installedAtMillis: Long,
    val isDefault: Boolean
)

@Entity(
    tableName = "profile",
    foreignKeys = [
        ForeignKey(
            entity = DistroEntity::class,
            parentColumns = ["id"],
            childColumns = ["distroId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("name", unique = true), Index("distroId")]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val colorSchemeId: String,
    val fontFamily: String,
    val fontSizeSp: Int,
    val scrollbackLines: Int,
    val distroId: Long?,
    val user: String?,
    val startupCommand: String?
)

/** [tree] is the pane tree as JSON (see LayoutCodec). */
@Entity(tableName = "layout", indices = [Index("name", unique = true)])
data class LayoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val tree: String
)

@Entity(
    tableName = "ssh_host",
    foreignKeys = [
        ForeignKey(
            entity = DistroEntity::class,
            parentColumns = ["id"],
            childColumns = ["distroId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("name", unique = true), Index("distroId")]
)
data class SshHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val keyAlias: String?,
    val distroId: Long?
)

/** One app setting as text; [SettingKeys] lists the keys in use. */
@Entity(tableName = "setting")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
