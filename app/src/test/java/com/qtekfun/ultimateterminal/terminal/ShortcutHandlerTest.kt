// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.profile.ProfileCommands
import com.qtekfun.ultimateterminal.domain.session.FocusDirection
import com.qtekfun.ultimateterminal.domain.session.PaneCommands
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

private class RecordingPanes : PaneCommands {
    val calls = mutableListOf<String>()

    override fun splitHorizontal() {
        calls += "split-h"
    }

    override fun splitVertical() {
        calls += "split-v"
    }

    override fun closePane() {
        calls += "close"
    }

    override fun toggleZoom() {
        calls += "zoom"
    }

    override fun focus(direction: FocusDirection) {
        calls += "focus $direction"
    }
}

private class RecordingProfiles : ProfileCommands {
    val calls = mutableListOf<String>()

    override fun toggleBroadcast() {
        calls += "broadcast"
    }

    override fun saveLayout() {
        calls += "save-layout"
    }

    override fun openLayouts() {
        calls += "open-layouts"
    }
}

class ShortcutHandlerTest {
    private val tabs = RecordingTabs()
    private val panes = RecordingPanes()
    private val profiles = RecordingProfiles()
    private val clipboard = mutableListOf<String>()
    private val fontSize = FontSizeController()
    private val handler = ShortcutHandler(
        copy = { clipboard += "copy" },
        paste = { clipboard += "paste" },
        fontSize = fontSize,
        tabs = tabs,
        panes = panes,
        profiles = profiles
    )

    @Test
    fun thePaneShortcutsDriveThePanes() {
        handler.handle(AppShortcut.SplitHorizontal)
        handler.handle(AppShortcut.SplitVertical)
        handler.handle(AppShortcut.ClosePane)
        handler.handle(AppShortcut.ToggleZoom)
        handler.handle(AppShortcut.FocusLeft)
        handler.handle(AppShortcut.FocusRight)
        handler.handle(AppShortcut.FocusUp)
        handler.handle(AppShortcut.FocusDown)

        assertEquals(
            listOf(
                "split-h",
                "split-v",
                "close",
                "zoom",
                "focus Left",
                "focus Right",
                "focus Up",
                "focus Down"
            ),
            panes.calls
        )
        assertEquals(emptyList<String>(), tabs.calls)
    }

    @Test
    fun theBroadcastAndLayoutShortcutsAskForTheirScreens() {
        handler.handle(AppShortcut.ToggleBroadcast)
        handler.handle(AppShortcut.SaveLayout)
        handler.handle(AppShortcut.OpenLayouts)

        assertEquals(listOf("broadcast", "save-layout", "open-layouts"), profiles.calls)
        assertEquals(emptyList<String>(), panes.calls)
        assertEquals(emptyList<String>(), tabs.calls)
    }

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
