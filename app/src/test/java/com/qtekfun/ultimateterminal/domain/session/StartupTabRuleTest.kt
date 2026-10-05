// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupTabRuleTest {
    @Test
    fun aStaleScreenThatComesBackWithNoSessionsFinishes() {
        assertEquals(StartupTabRule.Action.FINISH, StartupTabRule.onResume(0, hadSessions = true))
    }

    @Test
    fun aColdStartWithNoSessionYetDoesNotFinish() {
        assertEquals(StartupTabRule.Action.NONE, StartupTabRule.onResume(0, hadSessions = false))
        assertEquals(
            StartupTabRule.Action.NONE,
            StartupTabRule.onSessionsChanged(0, hadSessions = false)
        )
    }

    @Test
    fun resumingWithSessionsDoesNothing() {
        assertEquals(StartupTabRule.Action.NONE, StartupTabRule.onResume(2, hadSessions = true))
    }

    @Test
    fun reachingZeroAfterHavingSessionsFinishes() {
        assertEquals(
            StartupTabRule.Action.FINISH,
            StartupTabRule.onSessionsChanged(0, hadSessions = true)
        )
    }

    @Test
    fun remainingSessionsNeverFinishTheScreen() {
        assertEquals(
            StartupTabRule.Action.NONE,
            StartupTabRule.onSessionsChanged(1, hadSessions = true)
        )
    }
}
