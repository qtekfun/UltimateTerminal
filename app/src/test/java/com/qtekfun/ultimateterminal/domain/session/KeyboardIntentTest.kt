// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyboardIntentTest {
    @Test
    fun `a tap shows and a covering screen hides`() {
        assertTrue(KeyboardIntent.wantedAfter(KeyboardIntent.Request.SHOW))
        assertFalse(KeyboardIntent.wantedAfter(KeyboardIntent.Request.HIDE))
    }

    private fun restore(wanted: Boolean, hasFocus: Boolean, windowFocused: Boolean = true) =
        KeyboardIntent.shouldRestore(wanted, hasFocus, windowFocused)

    @Test
    fun `focus lost to a layout change is given back when the keyboard is wanted`() {
        assertTrue(restore(wanted = true, hasFocus = false))
    }

    @Test
    fun `nothing is restored when the focus is still there`() {
        assertFalse(restore(wanted = true, hasFocus = true))
    }

    @Test
    fun `nothing is restored after the keyboard was hidden on purpose`() {
        assertFalse(restore(wanted = false, hasFocus = false))
    }

    @Test
    fun `nothing is restored while another window has the focus`() {
        assertFalse(restore(wanted = true, hasFocus = false, windowFocused = false))
    }
}
