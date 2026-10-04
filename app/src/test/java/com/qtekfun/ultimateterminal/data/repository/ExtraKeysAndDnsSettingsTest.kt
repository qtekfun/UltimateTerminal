// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.data.local.runDatabaseTest
import com.qtekfun.ultimateterminal.domain.launch.ResolvConf
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExtraKeysAndDnsSettingsTest {
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
    fun `a fresh install has the default row and the built-in DNS servers`() = runDatabaseTest {
        val now = settings.observe().first()

        assertEquals(ExtraKeysConfig.default(), now.extraKeys)
        assertEquals(ResolvConf.FALLBACK_SERVERS, now.dnsFallbackServers)
    }

    @Test
    fun `the extra keys are stored and read back with their options`() = runDatabaseTest {
        val config = ExtraKeysConfig(
            listOf(listOf("esc", "tab"), listOf("up", "down")),
            visible = false,
            onlyWithKeyboard = false
        )

        settings.update { it.copy(extraKeys = config) }

        assertEquals(config, settings.observe().first().extraKeys)
    }

    @Test
    fun `the DNS servers are stored in order and read back`() = runDatabaseTest {
        settings.update { it.copy(dnsFallbackServers = listOf("9.9.9.9", "2620:fe::fe")) }

        assertEquals(
            listOf("9.9.9.9", "2620:fe::fe"),
            settings.observe().first().dnsFallbackServers
        )
    }

    @Test
    fun `a damaged DNS value keeps only what is an address`() = runDatabaseTest {
        db.settingDao().upsert(
            listOf(SettingEntity("dns_fallback", "1.1.1.1, not-a-server, 8.8.8.8"))
        )

        assertEquals(listOf("1.1.1.1", "8.8.8.8"), settings.observe().first().dnsFallbackServers)
    }

    @Test
    fun `an unreadable extra keys value gives the default row`() = runDatabaseTest {
        db.settingDao().upsert(listOf(SettingEntity("extra_keys", "nothing useful here")))

        assertEquals(ExtraKeysConfig.default().rows, settings.observe().first().extraKeys.rows)
        assertFalse(settings.observe().first().extraKeys.rows.isEmpty())
    }
}
