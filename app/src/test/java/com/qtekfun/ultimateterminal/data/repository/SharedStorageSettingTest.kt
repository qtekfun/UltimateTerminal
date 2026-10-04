// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SharedStorageSettingTest {
    private lateinit var db: UltimateTerminalDatabase
    private lateinit var settings: RoomSettingsRepository

    @BeforeEach
    fun setUp() {
        db = inMemoryDatabase()
        settings = RoomSettingsRepository(db.settingDao())
    }

    @AfterEach
    fun tearDown() = db.close()

    @Test
    fun `the shared storage is off on a fresh install`() = runTest {
        assertFalse(settings.observe().first().sharedStorage)
    }

    @Test
    fun `the shared storage setting is stored and read back`() = runTest {
        settings.update { it.copy(sharedStorage = true) }

        assertTrue(settings.observe().first().sharedStorage)
    }

    @Test
    fun `an unreadable stored value falls back to off`() = runTest {
        db.settingDao().upsert(listOf(SettingEntity("shared_storage", "maybe")))

        assertFalse(settings.observe().first().sharedStorage)
    }
}
