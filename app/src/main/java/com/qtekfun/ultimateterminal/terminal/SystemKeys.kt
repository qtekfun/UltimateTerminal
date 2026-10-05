// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.view.KeyEvent

/**
 * Keys that belong to the system or to the app's navigation, never to the shell. The input view
 * must leave them alone (not consume them, not encode them): if it consumed Back while it still
 * held focus under another screen, that screen's Back handler would never run (found on a tablet),
 * and a Back that reaches the shell is typed as a Tab.
 */
object SystemKeys {
    private val keys = setOf(
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_SLEEP,
        KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE
    )

    fun isSystemKey(keyCode: Int): Boolean = keyCode in keys
}
