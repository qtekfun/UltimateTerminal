// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtraKeysTest {
    @Test
    fun theDefaultConfigResolvesEveryKey() {
        val config = ExtraKeysConfig.default()
        val resolved = config.resolved()
        assertEquals(2, resolved.size)
        assertTrue(resolved.all { it.size == 7 })
        assertEquals(config.rows.flatten().size, resolved.flatten().size)
    }

    @Test
    fun catalogIdsAreUnique() {
        val ids = ExtraKeyCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertNull(ExtraKeyCatalog.find("nope"))
        assertEquals("ESC", ExtraKeyCatalog.find("esc")?.symbol)
    }

    @Test
    fun resolvedSkipsUnknownIdsAndEmptyRows() {
        val config = ExtraKeysConfig(listOf(listOf("esc", "ghost"), listOf("ghost")))
        val resolved = config.resolved()
        assertEquals(1, resolved.size)
        assertEquals(listOf("esc"), resolved[0].map { it.id })
    }

    @Test
    fun serializeAndParseRoundTrip() {
        val config = ExtraKeysConfig(listOf(listOf("esc", "tab"), listOf("up", "pipe")), false)
        val (parsed, rejected) = ExtraKeysConfig.parse(config.serialize())
        assertEquals(config, parsed)
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun parseReportsUnknownKeysAndKeepsTheRest() {
        val (config, rejected) = ExtraKeysConfig.parse("esc bogus tab\n\n  \nup\n")
        assertEquals(listOf(listOf("esc", "tab"), listOf("up")), config.rows)
        assertEquals(listOf("bogus"), rejected.map { it.line })
        assertTrue(config.visible)
    }

    @Test
    fun parseFallsBackToTheDefaultRowsButKeepsVisibility() {
        val (config, rejected) = ExtraKeysConfig.parse("visible=false\nbogus\n")
        assertEquals(ExtraKeysConfig.default().rows, config.rows)
        assertFalse(config.visible)
        assertEquals(1, rejected.size)
    }

    @Test
    fun parseOfNothingIsTheDefault() {
        assertEquals(ExtraKeysConfig.default(), ExtraKeysConfig.parse("").first)
    }

    @Test
    fun keyActionsAreWhatTheLabelsPromise() {
        assertEquals(
            ExtraKeyAction.Press(KeyEvent.KEYCODE_ESCAPE),
            ExtraKeyCatalog.find("esc")?.action
        )
        assertEquals(ExtraKeyAction.Modifier(StickyKey.CTRL), ExtraKeyCatalog.find("ctrl")?.action)
        assertEquals(ExtraKeyAction.Type("\\"), ExtraKeyCatalog.find("backslash")?.action)
        assertNotNull(ExtraKeyCatalog.find("pgdn"))
    }

    @Test
    fun heightIsRowsTimesRowHeightOrNothingWhenHidden() {
        val config = ExtraKeysConfig.default()
        assertEquals(96, extraKeysHeightPx(config, 48))
        assertEquals(0, extraKeysHeightPx(config.copy(visible = false), 48))
        assertEquals(
            EdgeInsets(1, 2, 3, 4 + 96),
            EdgeInsets(1, 2, 3, 4).reserveBottom(96)
        )
    }
}
