// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.broadcast

import android.view.KeyEvent
import com.qtekfun.ultimateterminal.domain.terminal.KeyInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputKindTest {
    @Test
    fun charactersEnterTabAndBackspaceAreTyping() {
        for (code in listOf(
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_SPACE
        )) {
            assertEquals(InputKind.TEXT, KeyInput(code, 0).inputKind(), "key $code")
        }
        assertEquals(
            InputKind.TEXT,
            KeyInput(KeyEvent.KEYCODE_A, 'A'.code, shift = true).inputKind()
        )
    }

    @Test
    fun chordsWithCtrlOrAltAreControl() {
        assertEquals(InputKind.CONTROL, KeyInput(KeyEvent.KEYCODE_C, 3, ctrl = true).inputKind())
        assertEquals(InputKind.CONTROL, KeyInput(KeyEvent.KEYCODE_B, 0, alt = true).inputKind())
    }

    @Test
    fun navigationAndFunctionKeysAreControl() {
        for (code in listOf(
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_F1,
            KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F12
        )) {
            assertEquals(InputKind.CONTROL, KeyInput(code, 0).inputKind(), "key $code")
        }
    }
}
