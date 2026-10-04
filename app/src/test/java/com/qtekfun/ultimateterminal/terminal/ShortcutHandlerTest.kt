// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.session.TabCommands
import com.qtekfun.ultimateterminal.domain.session.TabSwitch
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private class RecordingTabs : TabCommands {
    val calls = mutableListOf<String>()

    override fun newTab() {
        calls += "new"
    }

    override fun requestCloseActive() {
        calls += "close"
    }

    override fun switchTo(target: TabSwitch) {
        calls += "switch $target"
    }
}

class ShortcutHandlerTest {
    private val tabs = RecordingTabs()
    private val clipboard = mutableListOf<String>()
    private val fontSize = FontSizeController()
    private val handler = ShortcutHandler(
        copy = { clipboard += "copy" },
        paste = { clipboard += "paste" },
        fontSize = fontSize,
        tabs = tabs
    )

    @Test
    fun theTabShortcutsDriveTheTabs() {
        handler.handle(AppShortcut.NewTab)
        handler.handle(AppShortcut.CloseTab)
        handler.handle(AppShortcut.NextTab)
        handler.handle(AppShortcut.PreviousTab)
        handler.handle(AppShortcut.SelectTab(3))

        assertEquals(
            listOf(
                "new",
                "close",
                "switch ${TabSwitch.Next}",
                "switch ${TabSwitch.Previous}",
                "switch ${TabSwitch.Number(3)}"
            ),
            tabs.calls
        )
    }

    @Test
    fun copyAndPasteAreForwarded() {
        handler.handle(AppShortcut.Copy)
        handler.handle(AppShortcut.Paste)

        assertEquals(listOf("copy", "paste"), clipboard)
        assertEquals(emptyList<String>(), tabs.calls)
    }

    @Test
    fun theZoomShortcutsChangeTheFontSize() {
        val start = fontSize.sizeSp.value

        handler.handle(AppShortcut.ZoomIn)
        val bigger = fontSize.sizeSp.value
        handler.handle(AppShortcut.ZoomOut)
        handler.handle(AppShortcut.ZoomOut)
        val smaller = fontSize.sizeSp.value
        handler.handle(AppShortcut.ZoomReset)

        assertEquals(true, bigger > start)
        assertEquals(true, smaller < start)
        assertEquals(start, fontSize.sizeSp.value)
    }
}
