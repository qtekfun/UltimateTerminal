// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionsTest {
    private fun twoSessions(): Triple<Sessions, SessionId, SessionId> {
        val (one, first) = Sessions().created()
        val (two, second) = one.created()
        return Triple(two, first, second)
    }

    @Test
    fun aNewSessionIsRunningAndActive() {
        val (sessions, id) = Sessions().created()

        assertEquals(listOf(SessionInfo(id, SessionState.Running)), sessions.items)
        assertEquals(id, sessions.activeId)
        assertEquals(1, sessions.runningCount)
        assertTrue(sessions.needsService)
    }

    @Test
    fun idsAreUniqueAndTheNewestSessionBecomesActive() {
        val (sessions, first, second) = twoSessions()

        assertEquals(SessionId(1), first)
        assertEquals(SessionId(2), second)
        assertEquals(second, sessions.activeId)
    }

    @Test
    fun noSessionsMeansNoService() {
        val sessions = Sessions()

        assertFalse(sessions.needsService)
        assertNull(sessions.active)
    }

    @Test
    fun anExitedSessionStaysListedButNoLongerNeedsTheService() {
        val (sessions, id) = Sessions().created()

        val ended = sessions.exited(id, 3)

        assertEquals(SessionState.Exited(3), ended.active?.state)
        assertEquals(0, ended.runningCount)
        assertFalse(ended.needsService)
        assertEquals(1, ended.items.size)
    }

    @Test
    fun theServiceStaysWhileAnyShellRuns() {
        val (sessions, first, _) = twoSessions()

        assertTrue(sessions.exited(first, 0).needsService)
    }

    @Test
    fun anExitOfAnUnknownSessionIsIgnored() {
        val (sessions, _) = Sessions().created()

        assertSame(sessions, sessions.exited(SessionId(99), 1))
    }

    @Test
    fun theFirstExitStatusWins() {
        val (sessions, id) = Sessions().created()
        val ended = sessions.exited(id, 1)

        assertSame(ended, ended.exited(id, 2))
    }

    @Test
    fun closingTheActiveSessionPrefersARunningOne() {
        val (sessions, first, second) = twoSessions()
        val (three, third) = sessions.created()
        // Order: 1 running, 2 ended, 3 running (active). Closing 3 leaves 1 (running) over 2 (ended).
        val mixed = three.exited(second, 0)

        val closed = mixed.closed(third)

        assertEquals(first, closed.activeId)
    }

    @Test
    fun closingTheActiveSessionFallsBackToTheLastOneWhenNoneRuns() {
        val (sessions, first, second) = twoSessions()
        val allEnded = sessions.exited(first, 0).exited(second, 0)

        val closed = allEnded.closed(second)

        assertEquals(first, closed.activeId)
        assertFalse(closed.needsService)
    }

    @Test
    fun closingAnInactiveSessionKeepsTheActiveOne() {
        val (sessions, first, second) = twoSessions()

        val closed = sessions.closed(first)

        assertEquals(second, closed.activeId)
        assertEquals(1, closed.items.size)
    }

    @Test
    fun closingTheLastSessionLeavesNothingActive() {
        val (sessions, id) = Sessions().created()

        val closed = sessions.closed(id)

        assertTrue(closed.items.isEmpty())
        assertNull(closed.activeId)
    }

    @Test
    fun closingAnUnknownSessionChangesNothing() {
        val (sessions, _) = Sessions().created()

        assertSame(sessions, sessions.closed(SessionId(42)))
    }

    @Test
    fun closeAllDoesNotReuseIds() {
        val (sessions, _, _) = twoSessions()

        val (next, id) = sessions.allClosed().created()

        assertEquals(SessionId(3), id)
        assertEquals(1, next.items.size)
    }

    @Test
    fun activatingChangesTheActiveSessionOnlyForKnownIds() {
        val (sessions, first, second) = twoSessions()

        assertEquals(first, sessions.activated(first).activeId)
        assertSame(sessions, sessions.activated(SessionId(77)))
        assertEquals(second, sessions.activeId)
    }

    @Test
    fun theWakeLockNeedsBothTheSettingAndARunningShell() {
        val (running, id) = Sessions().created()
        val ended = running.exited(id, 0)

        assertTrue(wakeLockWanted(keepAwake = true, sessions = running))
        assertFalse(wakeLockWanted(keepAwake = false, sessions = running))
        assertFalse(wakeLockWanted(keepAwake = true, sessions = ended))
        assertFalse(wakeLockWanted(keepAwake = true, sessions = Sessions()))
    }
}
