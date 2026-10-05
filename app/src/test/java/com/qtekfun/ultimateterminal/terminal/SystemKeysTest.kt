// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemKeysTest {
    @Test
    fun backAndTheOtherNavigationKeysAreLeftToTheSystem() {
        listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN
        ).forEach { assertTrue(SystemKeys.isSystemKey(it), "key $it") }
    }

    @Test
    fun theKeysOfAShellAreNotSystemKeys() {
        listOf(
            KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_DPAD_UP
        ).forEach { assertFalse(SystemKeys.isSystemKey(it), "key $it") }
    }
}
