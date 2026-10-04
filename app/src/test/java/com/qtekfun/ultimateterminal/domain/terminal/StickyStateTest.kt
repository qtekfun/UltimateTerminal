// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StickyStateTest {
    @Test
    fun tappingCyclesOffArmedLockedOff() {
        var state = StickyState()
        state = state.tap(StickyKey.CTRL)
        assertEquals(LatchState.ARMED, state.ctrl)
        state = state.tap(StickyKey.CTRL)
        assertEquals(LatchState.LOCKED, state.ctrl)
        state = state.tap(StickyKey.CTRL)
        assertEquals(LatchState.OFF, state.ctrl)
    }

    @Test
    fun theTwoModifiersAreIndependent() {
        val state = StickyState().tap(StickyKey.ALT)
        assertEquals(LatchState.OFF, state.ctrl)
        assertEquals(LatchState.ARMED, state.alt)
        assertEquals(LatchState.ARMED, state[StickyKey.ALT])
        assertEquals(LatchState.OFF, state[StickyKey.CTRL])
    }

    @Test
    fun afterAKeyArmedIsSpentAndLockedStays() {
        val state = StickyState().tap(StickyKey.CTRL).tap(StickyKey.ALT).tap(StickyKey.ALT)
        val after = state.afterKey()
        assertEquals(LatchState.OFF, after.ctrl)
        assertEquals(LatchState.LOCKED, after.alt)
    }

    @Test
    fun activeFlagsFollowTheState() {
        assertFalse(StickyState().anyActive)
        val armed = StickyState().tap(StickyKey.CTRL)
        assertTrue(armed.ctrlActive)
        assertFalse(armed.altActive)
        assertTrue(armed.anyActive)
    }
}
