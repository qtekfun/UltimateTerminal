// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ForegroundPolicyTest {
    private val none = Sessions()
    private val one = none.created().first
    private val ended = one.exited(one.items.first().id, 0)

    @Test
    fun zeroSessionsHaveNoNotification() {
        assertNull(ForegroundPolicy.notificationCount(none))
    }

    @Test
    fun onlyEndedSessionsHaveNoNotification() {
        assertNull(ForegroundPolicy.notificationCount(ended))
    }

    @Test
    fun runningSessionsAreCounted() {
        assertEquals(2, ForegroundPolicy.notificationCount(one.created().first))
    }

    @Test
    fun exitSequenceGoesFromStayToStop() {
        assertEquals(ForegroundPolicy.Step.STAY, ForegroundPolicy.step(one))
        assertEquals(ForegroundPolicy.Step.STOP_AND_REMOVE, ForegroundPolicy.step(one.allClosed()))
        assertEquals(ForegroundPolicy.Step.STOP_AND_REMOVE, ForegroundPolicy.step(ended))
    }
}
