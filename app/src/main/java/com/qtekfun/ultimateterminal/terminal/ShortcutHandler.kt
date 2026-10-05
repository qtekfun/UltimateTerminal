// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.profile.ProfileCommands
import com.qtekfun.ultimateterminal.domain.session.FocusDirection
import com.qtekfun.ultimateterminal.domain.session.PaneCommands
import com.qtekfun.ultimateterminal.domain.session.TabCommands
import com.qtekfun.ultimateterminal.domain.session.TabSwitch
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut

/**
 * Carries out the application shortcuts: copy, paste, the font zoom, the tabs, the panes, and the
 * broadcast and the saved layouts.
 */
class ShortcutHandler(
    private val copy: () -> Unit,
    private val paste: () -> Unit,
    private val fontSize: FontSizeController,
    private val tabs: TabCommands,
    private val panes: PaneCommands,
    private val profiles: ProfileCommands
) {
    fun handle(shortcut: AppShortcut) {
        when (shortcut) {
            AppShortcut.Copy -> copy()
            AppShortcut.Paste -> paste()
            AppShortcut.ZoomIn -> fontSize.zoomIn()
            AppShortcut.ZoomOut -> fontSize.zoomOut()
            AppShortcut.ZoomReset -> fontSize.reset()
            else -> if (!handleTab(shortcut)) handlePane(shortcut)
        }
    }

    /** True if [shortcut] was one of the tab shortcuts. */
    private fun handleTab(shortcut: AppShortcut): Boolean {
        when (shortcut) {
            AppShortcut.NewTab -> tabs.newTab()
            AppShortcut.CloseTab -> tabs.requestCloseActive()
            AppShortcut.NextTab -> tabs.switchTo(TabSwitch.Next)
            AppShortcut.PreviousTab -> tabs.switchTo(TabSwitch.Previous)
            is AppShortcut.SelectTab -> tabs.switchTo(TabSwitch.Number(shortcut.number))
            else -> return false
        }
        return true
    }

    private fun handleProfile(shortcut: AppShortcut) {
        when (shortcut) {
            AppShortcut.ToggleBroadcast -> profiles.toggleBroadcast()
            AppShortcut.SaveLayout -> profiles.saveLayout()
            AppShortcut.OpenLayouts -> profiles.openLayouts()
            else -> Unit
        }
    }

    private fun handlePane(shortcut: AppShortcut) {
        when (shortcut) {
            AppShortcut.SplitHorizontal -> panes.splitHorizontal()
            AppShortcut.SplitVertical -> panes.splitVertical()
            AppShortcut.ClosePane -> panes.closePane()
            AppShortcut.ToggleZoom -> panes.toggleZoom()
            AppShortcut.FocusLeft -> panes.focus(FocusDirection.Left)
            AppShortcut.FocusRight -> panes.focus(FocusDirection.Right)
            AppShortcut.FocusUp -> panes.focus(FocusDirection.Up)
            AppShortcut.FocusDown -> panes.focus(FocusDirection.Down)
            else -> handleProfile(shortcut)
        }
    }
}
