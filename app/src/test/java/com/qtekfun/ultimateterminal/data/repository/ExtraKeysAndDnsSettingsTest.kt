// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import android.view.KeyEvent
import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.domain.launch.ResolvConf
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
    fun `a fresh install has the default row and the built-in DNS servers`() = runTest {
        val now = settings.observe().first()

        assertEquals(ExtraKeysConfig.default(), now.extraKeys)
        assertEquals(ResolvConf.FALLBACK_SERVERS, now.dnsFallbackServers)
    }

    @Test
    fun `the extra keys are stored and read back with their options`() = runTest {
        val config = ExtraKeysConfig(
            listOf(listOf("esc", "tab"), listOf("up", "down")),
            visible = false,
            onlyWithKeyboard = false
        )

        settings.update { it.copy(extraKeys = config) }

        assertEquals(config, settings.observe().first().extraKeys)
    }

    @Test
    fun `the DNS servers are stored in order and read back`() = runTest {
        settings.update { it.copy(dnsFallbackServers = listOf("9.9.9.9", "2620:fe::fe")) }

        assertEquals(
            listOf("9.9.9.9", "2620:fe::fe"),
            settings.observe().first().dnsFallbackServers
        )
    }

    @Test
    fun `a damaged DNS value keeps only what is an address`() = runTest {
        db.settingDao().upsert(
            listOf(SettingEntity("dns_fallback", "1.1.1.1, not-a-server, 8.8.8.8"))
        )

        assertEquals(listOf("1.1.1.1", "8.8.8.8"), settings.observe().first().dnsFallbackServers)
    }

    @Test
    fun `an unreadable extra keys value gives the default row`() = runTest {
        db.settingDao().upsert(listOf(SettingEntity("extra_keys", "nothing useful here")))

        assertEquals(ExtraKeysConfig.default().rows, settings.observe().first().extraKeys.rows)
        assertFalse(settings.observe().first().extraKeys.rows.isEmpty())
    }

    @Test
    fun `a fresh install has the default shortcuts`() = runTest {
        assertEquals(ShortcutMap.defaults(), settings.observe().first().shortcuts)
    }

    @Test
    fun `the shortcuts are stored and read back`() = runTest {
        val mine = ShortcutMap.defaults()
            .unbind(KeyChord(KeyEvent.KEYCODE_T, ctrl = true, shift = true))
            .bind(KeyChord(KeyEvent.KEYCODE_K, ctrl = true, alt = true), AppShortcut.NewTab)

        settings.update { it.copy(shortcuts = mine) }

        assertEquals(mine, settings.observe().first().shortcuts)
    }

    @Test
    fun `a stored shortcut line that makes no sense is skipped and the others are kept`() = runTest {
        db.settingDao().upsert(
            listOf(
                SettingEntity(SettingKeys.SHORTCUTS, "ctrl+alt+k=new_tab\nnot a line\nq=copy\n")
            )
        )

        assertEquals(
            mapOf(KeyChord(KeyEvent.KEYCODE_K, ctrl = true, alt = true) to AppShortcut.NewTab),
            settings.observe().first().shortcuts.all
        )
    }
}
