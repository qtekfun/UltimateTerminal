// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.repository

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.entity.LayoutEntity
import com.qtekfun.ultimateterminal.data.local.entity.SettingEntity
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LayoutAndSettingsRepositoryTest {
    private lateinit var db: UltimateTerminalDatabase
    private lateinit var layouts: RoomLayoutRepository
    private lateinit var settings: RoomSettingsRepository

    @BeforeEach
    fun setUp() {
        db = inMemoryDatabase()
        layouts = RoomLayoutRepository(db.layoutDao())
        settings = RoomSettingsRepository(db.settingDao())
    }

    @AfterEach
    fun tearDown() = db.close()

    private fun failure(outcome: Outcome<*>) = (outcome as Outcome.Failure).error

    private val tree = LayoutNode.Split(
        orientation = SplitOrientation.VERTICAL,
        ratio = 0.4f,
        first = LayoutNode.Pane(profileId = 3L, command = "ssh prod"),
        second = LayoutNode.Split(
            SplitOrientation.HORIZONTAL,
            0.5f,
            LayoutNode.Pane(),
            LayoutNode.Pane(command = "htop")
        )
    )

    @Test
    fun `a layout keeps its whole pane tree`() = runTest {
        val saved = checkNotNull(layouts.add(Layout(name = " servers ", root = tree)).getOrNull())

        assertEquals("servers", saved.name)
        assertEquals(saved, layouts.get(saved.id))
        assertEquals(listOf(saved), layouts.observeAll().first())
        assertEquals(tree, layouts.get(saved.id)?.root)
    }

    @Test
    fun `layouts check names`() = runTest {
        layouts.add(Layout(name = "one", root = tree))

        assertEquals(
            DomainError.NameTaken("ONE"),
            failure(layouts.add(Layout(name = "ONE", root = tree)))
        )
        assertTrue(failure(layouts.add(Layout(name = "", root = tree))) is DomainError.InvalidName)
    }

    @Test
    fun `a layout can be updated and removed`() = runTest {
        val one = checkNotNull(layouts.add(Layout(name = "one", root = tree)).getOrNull())
        layouts.add(Layout(name = "two", root = tree))

        assertEquals(
            Outcome.Success(Unit),
            layouts.update(one.copy(name = "uno", root = LayoutNode.Pane()))
        )
        assertEquals(LayoutNode.Pane(), layouts.get(one.id)?.root)
        assertEquals(DomainError.NameTaken("two"), failure(layouts.update(one.copy(name = "two"))))
        assertEquals(DomainError.NotFound, failure(layouts.update(one.copy(id = 99L))))
        assertTrue(failure(layouts.update(one.copy(name = " "))) is DomainError.InvalidName)

        assertEquals(Outcome.Success(Unit), layouts.remove(one.id))
        assertNull(layouts.get(one.id))
        assertEquals(DomainError.NotFound, failure(layouts.remove(one.id)))
    }

    @Test
    fun `a layout whose stored tree is damaged is left out`() = runTest {
        val good = checkNotNull(layouts.add(Layout(name = "good", root = tree)).getOrNull())
        val badRatio = db.layoutDao().insert(
            LayoutEntity(
                name = "bad",
                tree = """{"type":"split","orientation":"VERTICAL","ratio":2.0,""" +
                    """"first":{"type":"pane"},"second":{"type":"pane"}}"""
            )
        )
        db.layoutDao().insert(LayoutEntity(name = "garbage", tree = "not json"))

        assertEquals(listOf(good), layouts.observeAll().first())
        assertNull(layouts.get(badRatio))
    }

    @Test
    fun `settings start with the defaults`() = runTest {
        assertEquals(AppSettings(), settings.observe().first())
    }

    @Test
    fun `settings are stored and read back`() = runTest {
        settings.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                oledBlack = true,
                dynamicColor = false,
                keepAwake = true,
                defaultScrollbackLines = 2_000
            )
        }

        val stored = settings.observe().first()
        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertTrue(stored.oledBlack)
        assertEquals(false, stored.dynamicColor)
        assertTrue(stored.keepAwake)
        assertEquals(2_000, stored.defaultScrollbackLines)
    }

    @Test
    fun `an update changes only what the transform changes`() = runTest {
        settings.update { it.copy(oledBlack = true) }
        settings.update { it.copy(keepAwake = true) }

        assertEquals(AppSettings(oledBlack = true, keepAwake = true), settings.observe().first())
    }

    @Test
    fun `an out of range scrollback is clamped when saved`() = runTest {
        settings.update { it.copy(defaultScrollbackLines = 1) }

        assertEquals(100, settings.observe().first().defaultScrollbackLines)
    }

    @Test
    fun `unreadable stored values fall back to their defaults`() = runTest {
        db.settingDao().upsert(
            listOf(
                SettingEntity("theme_mode", "PURPLE"),
                SettingEntity("oled_black", "maybe"),
                SettingEntity("dynamic_color", ""),
                SettingEntity("keep_awake", "1"),
                SettingEntity("default_scrollback_lines", "5")
            )
        )

        assertEquals(AppSettings(), settings.observe().first())
    }
}
