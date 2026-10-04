// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettledLayoutTest {
    private fun layout(rows: Int, columns: Int = 80) =
        TerminalLayout(GridSize(columns, rows), 10, 20)

    private val requests = MutableSharedFlow<TerminalLayout>(extraBufferCapacity = 64)

    @Test
    fun theFirstLayoutPassesAtOnce() = runTest {
        val delivered = mutableListOf<TerminalLayout>()
        val job = launch { requests.settled(100).collect { delivered += it } }
        runCurrent()

        requests.emit(layout(24))
        runCurrent()

        assertEquals(listOf(layout(24)), delivered)
        job.cancel()
    }

    @Test
    fun aBurstOfChangesDeliversOnlyTheLastOne() = runTest {
        val delivered = mutableListOf<TerminalLayout>()
        val job = launch { requests.settled(100).collect { delivered += it } }
        runCurrent()
        requests.emit(layout(24))
        runCurrent()

        // The keyboard rises over 8 frames of 16 ms each.
        for (rows in 23 downTo 16) {
            requests.emit(layout(rows))
            advanceTimeBy(16)
        }
        runCurrent()
        assertEquals(listOf(layout(24)), delivered)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(layout(24), layout(16)), delivered)
        job.cancel()
    }

    @Test
    fun aChangeIsDeliveredOnceTheDelayHasPassed() = runTest {
        val delivered = mutableListOf<TerminalLayout>()
        val job = launch { requests.settled(100).collect { delivered += it } }
        runCurrent()
        requests.emit(layout(24))
        runCurrent()

        requests.emit(layout(40))
        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, delivered.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(layout(24), layout(40)), delivered)
        job.cancel()
    }

    @Test
    fun equalLayoutsAreDropped() = runTest {
        val delivered = mutableListOf<TerminalLayout>()
        val job = launch { requests.settled(100).collect { delivered += it } }
        runCurrent()

        requests.emit(layout(24))
        requests.emit(layout(24))
        advanceTimeBy(500)
        runCurrent()
        requests.emit(layout(24))
        advanceTimeBy(500)
        runCurrent()

        assertEquals(listOf(layout(24)), delivered)
        job.cancel()
    }

    @Test
    fun rotatingBackAndForthWithinTheDelayEndsOnTheFinalSize() = runTest {
        val delivered = mutableListOf<TerminalLayout>()
        val job = launch { requests.settled(100).collect { delivered += it } }
        runCurrent()
        requests.emit(layout(24))
        runCurrent()

        requests.emit(layout(24, columns = 40))
        advanceTimeBy(30)
        requests.emit(layout(24))
        advanceTimeBy(500)
        runCurrent()

        // The size that settled equals the one already applied: the consumer sees it twice and
        // must treat the second as a no-op (TerminalSessionHost.resize does).
        assertEquals(listOf(layout(24), layout(24)), delivered)
        job.cancel()
    }

    @Test
    fun aNegativeDelayIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { requests.settled(-1) }
    }
}
