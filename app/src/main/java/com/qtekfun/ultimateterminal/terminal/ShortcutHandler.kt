// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.session.TabCommands
import com.qtekfun.ultimateterminal.domain.session.TabSwitch
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut

/** Carries out the application shortcuts: copy, paste, the font zoom and the tab commands. */
class ShortcutHandler(
    private val copy: () -> Unit,
    private val paste: () -> Unit,
    private val fontSize: FontSizeController,
    private val tabs: TabCommands
) {
    fun handle(shortcut: AppShortcut) {
        when (shortcut) {
            AppShortcut.Copy -> copy()
            AppShortcut.Paste -> paste()
            AppShortcut.ZoomIn -> fontSize.zoomIn()
            AppShortcut.ZoomOut -> fontSize.zoomOut()
            AppShortcut.ZoomReset -> fontSize.reset()
            AppShortcut.NewTab -> tabs.newTab()
            AppShortcut.CloseTab -> tabs.requestCloseActive()
            AppShortcut.NextTab -> tabs.switchTo(TabSwitch.Next)
            AppShortcut.PreviousTab -> tabs.switchTo(TabSwitch.Previous)
            is AppShortcut.SelectTab -> tabs.switchTo(TabSwitch.Number(shortcut.number))
        }
    }
}
