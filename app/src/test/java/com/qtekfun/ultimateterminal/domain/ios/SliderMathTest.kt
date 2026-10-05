// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SliderMathTest {
    private val range = 8f..32f

    @Test
    fun aTouchOutsideTheTrackIsPinnedToItsEnds() {
        assertEquals(0f, SliderMath.fractionAt(-20f, 200f))
        assertEquals(0.25f, SliderMath.fractionAt(50f, 200f))
        assertEquals(1f, SliderMath.fractionAt(260f, 200f))
    }

    @Test
    fun aTrackWithNoWidthReadsAsTheStart() {
        assertEquals(0f, SliderMath.fractionAt(10f, 0f))
    }

    @Test
    fun theValueFollowsTheFractionAlongTheRange() {
        assertEquals(8f, SliderMath.valueAt(0f, range))
        assertEquals(20f, SliderMath.valueAt(0.5f, range))
        assertEquals(32f, SliderMath.valueAt(1.5f, range))
    }

    @Test
    fun theFractionIsTheInverseOfTheValueAndPinsStrangers() {
        assertEquals(0.5f, SliderMath.fractionOf(20f, range))
        assertEquals(0f, SliderMath.fractionOf(2f, range))
        assertEquals(1f, SliderMath.fractionOf(99f, range))
        assertEquals(0f, SliderMath.fractionOf(5f, 3f..3f))
    }

    @Test
    fun aScreenReaderStepsAFairShareOfTheRangeAndStopsAtTheEnds() {
        // 24 units over 20 steps: 1.2 per step.
        assertEquals(9.2f, SliderMath.stepped(8f, range, 1), 1e-4f)
        assertEquals(8f, SliderMath.stepped(8f, range, -1))
        assertEquals(32f, SliderMath.stepped(31.5f, range, 1))
    }
}
