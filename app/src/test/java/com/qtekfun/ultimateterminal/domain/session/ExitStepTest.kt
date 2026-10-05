// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExitStepTest {
    private fun sessions(vararg states: SessionState) = Sessions(
        items = states.mapIndexed { index, state -> SessionInfo(SessionId(index + 1), state) }
    )

    @Test
    fun `no sessions exits without asking`() {
        assertEquals(ExitStep.ExitNow, exitStep(Sessions()))
    }

    @Test
    fun `only ended sessions exit without asking`() {
        assertEquals(ExitStep.ExitNow, exitStep(sessions(SessionState.Exited(0))))
    }

    @Test
    fun `asks with the number of running sessions and ignores the ended ones`() {
        val mixed = sessions(SessionState.Running, SessionState.Exited(1), SessionState.Running)
        assertEquals(ExitStep.Ask(2), exitStep(mixed))
    }
}
