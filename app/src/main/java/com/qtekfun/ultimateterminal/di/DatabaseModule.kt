// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.dao.DistroDao
import com.qtekfun.ultimateterminal.data.local.dao.LayoutDao
import com.qtekfun.ultimateterminal.data.local.dao.ProfileDao
import com.qtekfun.ultimateterminal.data.local.dao.SettingDao
import com.qtekfun.ultimateterminal.data.local.dao.SshHostDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** Provides the database and its DAOs; repositories take the DAOs they need. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "ultimateterminal.db"

    // The spread copies a tiny array once, when the database is created.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): UltimateTerminalDatabase =
        Room.databaseBuilder<UltimateTerminalDatabase>(context, DATABASE_NAME)
            // The system SQLite keeps the APK small; tests use the bundled build with the same API.
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(ioDispatcher)
            .addMigrations(*UltimateTerminalDatabase.MIGRATIONS)
            .build()

    @Provides
    fun distroDao(db: UltimateTerminalDatabase): DistroDao = db.distroDao()

    @Provides
    fun profileDao(db: UltimateTerminalDatabase): ProfileDao = db.profileDao()

    @Provides
    fun layoutDao(db: UltimateTerminalDatabase): LayoutDao = db.layoutDao()

    @Provides
    fun sshHostDao(db: UltimateTerminalDatabase): SshHostDao = db.sshHostDao()

    @Provides
    fun settingDao(db: UltimateTerminalDatabase): SettingDao = db.settingDao()
}
