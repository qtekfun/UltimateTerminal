// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.data.local.runDatabaseTest
import com.qtekfun.ultimateterminal.domain.session.SidebarMode
import com.qtekfun.ultimateterminal.domain.terminal.KeyboardType
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProotCompatibilitySettingTest {
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
    fun `compatibility mode is off on a fresh install`() = runDatabaseTest {
        assertFalse(settings.observe().first().prootCompatibilityMode)
    }

    @Test
    fun `compatibility mode is stored and read back`() = runDatabaseTest {
        settings.update { it.copy(prootCompatibilityMode = true) }

        assertTrue(settings.observe().first().prootCompatibilityMode)
    }

    @Test
    fun `an unreadable stored value falls back to off`() = runDatabaseTest {
        db.settingDao().upsert(listOf(SettingEntity("proot_compatibility_mode", "yes please")))

        assertFalse(settings.observe().first().prootCompatibilityMode)
    }

    @Test
    fun `the sidebar collapses by default and the mode is stored and read back`() =
        runDatabaseTest {
            assertEquals(SidebarMode.AUTO_COLLAPSE, settings.observe().first().sidebarMode)

            settings.update { it.copy(sidebarMode = SidebarMode.ALWAYS_EXPANDED) }

            assertEquals(SidebarMode.ALWAYS_EXPANDED, settings.observe().first().sidebarMode)
        }

    @Test
    fun `the keyboard is normal by default and the type is stored and read back`() =
        runDatabaseTest {
            assertEquals(KeyboardType.NORMAL, settings.observe().first().keyboardType)

            settings.update { it.copy(keyboardType = KeyboardType.RAW) }

            assertEquals(KeyboardType.RAW, settings.observe().first().keyboardType)
        }

    @Test
    fun `an unreadable keyboard type falls back to the default`() = runDatabaseTest {
        db.settingDao().upsert(listOf(SettingEntity("keyboard_type", "secure")))

        assertEquals(KeyboardType.DEFAULT, settings.observe().first().keyboardType)
    }

    @Test
    fun `an unreadable sidebar mode falls back to the default`() = runDatabaseTest {
        db.settingDao().upsert(listOf(SettingEntity("sidebar_mode", "sideways")))

        assertEquals(SidebarMode.DEFAULT, settings.observe().first().sidebarMode)
    }
}
