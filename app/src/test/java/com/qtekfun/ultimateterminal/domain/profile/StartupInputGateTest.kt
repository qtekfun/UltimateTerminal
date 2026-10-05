// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StartupInputGateTest {
    private fun gate() = StartupInputGate(startMillis = 1_000, quietMillis = 300, maxWaitMillis = 5_000)

    @Test
    fun itWaitsWhileTheShellHasWrittenNothing() {
        assertFalse(gate().isReady(1_800))
    }

    @Test
    fun itIsReadyOnceTheOutputWentQuiet() {
        val gate = gate()
        gate.onOutput(1_500)

        assertFalse(gate.isReady(1_799))
        assertTrue(gate.isReady(1_800))
    }

    @Test
    fun moreOutputRestartsTheQuietTime() {
        val gate = gate()
        gate.onOutput(1_500)
        gate.onOutput(1_750)

        assertFalse(gate.isReady(1_900))
        assertTrue(gate.isReady(2_050))
    }

    @Test
    fun aSilentShellGetsItsCommandAtTheMaximumWait() {
        val gate = gate()

        assertFalse(gate.isReady(5_999))
        assertTrue(gate.isReady(6_000))
    }

    @Test
    fun aShellThatNeverGoesQuietGetsItAtTheMaximumWaitToo() {
        val gate = gate()
        (1_000L..6_000L step 100).forEach(gate::onOutput)

        assertTrue(gate.isReady(6_000))
    }
}
