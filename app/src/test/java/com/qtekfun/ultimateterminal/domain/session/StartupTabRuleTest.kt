// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupTabRuleTest {
    @Test
    fun resumingWithNoSessionOpensTheDefaultTab() {
        assertEquals(StartupTabRule.Action.OPEN_DEFAULT_TAB, StartupTabRule.onResume(0))
    }

    @Test
    fun resumingWithSessionsDoesNothing() {
        assertEquals(StartupTabRule.Action.NONE, StartupTabRule.onResume(2))
    }

    @Test
    fun theLastTabClosedInFrontClosesTheApp() {
        assertEquals(
            StartupTabRule.Action.FINISH,
            StartupTabRule.onSessionsChanged(0, hadSessions = true, resumed = true)
        )
    }

    @Test
    fun sessionsGoneWhileNotResumedDoNotFinishTheScreen() {
        assertEquals(
            StartupTabRule.Action.NONE,
            StartupTabRule.onSessionsChanged(0, hadSessions = true, resumed = false)
        )
    }

    @Test
    fun noSessionsFromTheStartDoNotFinishTheScreen() {
        assertEquals(
            StartupTabRule.Action.NONE,
            StartupTabRule.onSessionsChanged(0, hadSessions = false, resumed = true)
        )
    }

    @Test
    fun remainingSessionsNeverFinishTheScreen() {
        assertEquals(
            StartupTabRule.Action.NONE,
            StartupTabRule.onSessionsChanged(1, hadSessions = true, resumed = true)
        )
    }
}
