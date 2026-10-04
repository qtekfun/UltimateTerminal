// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import com.qtekfun.ultimateterminal.data.local.dao.DistroDao
import com.qtekfun.ultimateterminal.data.local.dao.LayoutDao
import com.qtekfun.ultimateterminal.data.local.dao.ProfileDao
import com.qtekfun.ultimateterminal.data.local.dao.SettingDao
import com.qtekfun.ultimateterminal.data.local.dao.SshHostDao
import com.qtekfun.ultimateterminal.data.local.entity.DistroEntity
import com.qtekfun.ultimateterminal.data.local.entity.LayoutEntity
import com.qtekfun.ultimateterminal.data.local.entity.ProfileEntity
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.entity.SshHostEntity

/** Local metadata store (SPEC §5). Schemas are exported to app/schemas and versioned. */
@Database(
    entities = [
        DistroEntity::class,
        ProfileEntity::class,
        LayoutEntity::class,
        SshHostEntity::class,
        SettingEntity::class
    ],
    version = UltimateTerminalDatabase.VERSION,
    exportSchema = true
)
abstract class UltimateTerminalDatabase : RoomDatabase() {
    companion object {
        const val VERSION = 1

        /**
         * Migrations from each released version to the next. There is no destructive fallback:
         * raising [VERSION] requires adding its migration here (checked by DatabaseSchemaTest).
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }

    abstract fun distroDao(): DistroDao

    abstract fun profileDao(): ProfileDao

    abstract fun layoutDao(): LayoutDao

    abstract fun sshHostDao(): SshHostDao

    abstract fun settingDao(): SettingDao
}
