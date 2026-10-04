// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Carries out the application shortcuts this screen can: copy, paste and the font zoom. The rest
 * (tabs, T09) are passed on to [unhandled], and nothing acts on them yet.
 */
class ShortcutHandler(
    private val copy: () -> Unit,
    private val paste: () -> Unit,
    private val fontSize: FontSizeController,
    private val unhandled: MutableSharedFlow<AppShortcut>
) {
    fun handle(shortcut: AppShortcut) {
        when (shortcut) {
            AppShortcut.Copy -> copy()
            AppShortcut.Paste -> paste()
            AppShortcut.ZoomIn -> fontSize.zoomIn()
            AppShortcut.ZoomOut -> fontSize.zoomOut()
            AppShortcut.ZoomReset -> fontSize.reset()
            else -> unhandled.tryEmit(shortcut)
        }
    }
}
