// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrollbackChoicesTest {
    @Test
    fun theOptionsAreAscendingAndInsideWhatTheEmulatorTakes() {
        val options = ScrollbackChoices.options

        assertEquals(options.sorted(), options)
        assertTrue(options.all { it in ScrollbackChoices.MIN_LINES..ScrollbackChoices.MAX_LINES })
        assertTrue(10_000 in options)
    }

    @Test
    fun anOddStoredValueMapsToTheClosestOption() {
        assertEquals(10_000, ScrollbackChoices.nearest(9_000))
        assertEquals(1_000, ScrollbackChoices.nearest(100))
        assertEquals(ScrollbackChoices.MAX_LINES, ScrollbackChoices.nearest(1_000_000))
        assertEquals(5_000, ScrollbackChoices.nearest(5_000))
    }

    @Test
    fun theEmulatorIsNeverGivenMoreOrLessThanItCanHold() {
        assertEquals(ScrollbackChoices.MAX_LINES, ScrollbackChoices.forEmulator(1_000_000))
        assertEquals(ScrollbackChoices.MIN_LINES, ScrollbackChoices.forEmulator(-5))
        assertEquals(7_000, ScrollbackChoices.forEmulator(7_000))
    }
}
