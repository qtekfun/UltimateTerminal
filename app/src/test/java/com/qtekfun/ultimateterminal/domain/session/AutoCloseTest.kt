// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoCloseTest {
    @Test
    fun aCleanExitClosesAtOnce() {
        assertTrue(AutoClose.shouldClose(0, 0))
        assertTrue(AutoClose.shouldClose(0, 60_000))
    }

    @Test
    fun aShellThatLivedAWhileClosesWhateverItsStatus() {
        assertTrue(AutoClose.shouldClose(130, 10_000))
        assertTrue(AutoClose.shouldClose(127, AutoClose.FAILED_START_MILLIS))
    }

    @Test
    fun aFailedStartIsKeptSoItsMessageCanBeRead() {
        assertFalse(AutoClose.shouldClose(SessionController.START_FAILED, 0))
        assertFalse(AutoClose.shouldClose(127, 300))
        assertFalse(AutoClose.shouldClose(126, AutoClose.FAILED_START_MILLIS - 1))
    }
}
