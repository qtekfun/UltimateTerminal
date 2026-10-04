// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LargeTitleTest {
    @Test
    fun theCollapseFollowsTheScrollAndStaysBetweenZeroAndOne() {
        assertEquals(0f, LargeTitle.collapseFraction(-30f, 100f))
        assertEquals(0f, LargeTitle.collapseFraction(0f, 100f))
        assertEquals(0.25f, LargeTitle.collapseFraction(25f, 100f))
        assertEquals(1f, LargeTitle.collapseFraction(100f, 100f))
        assertEquals(1f, LargeTitle.collapseFraction(400f, 100f))
    }

    @Test
    fun aTitleWithNothingToCollapseIsAlreadyCollapsed() {
        assertEquals(1f, LargeTitle.collapseFraction(0f, 0f))
        assertEquals(1f, LargeTitle.collapseFraction(10f, -5f))
    }

    @Test
    fun theLargeTitleFadesOutAsItCollapses() {
        assertEquals(1f, LargeTitle.largeTitleAlpha(0f))
        assertEquals(0.5f, LargeTitle.largeTitleAlpha(0.5f))
        assertEquals(0f, LargeTitle.largeTitleAlpha(1f))
        assertEquals(1f, LargeTitle.largeTitleAlpha(-1f))
        assertEquals(0f, LargeTitle.largeTitleAlpha(2f))
    }

    @Test
    fun theSmallTitleAppearsDuringTheSecondHalf() {
        assertEquals(0f, LargeTitle.smallTitleAlpha(0f))
        assertEquals(0f, LargeTitle.smallTitleAlpha(0.5f))
        assertEquals(0.5f, LargeTitle.smallTitleAlpha(0.75f))
        assertEquals(1f, LargeTitle.smallTitleAlpha(1f))
        assertEquals(1f, LargeTitle.smallTitleAlpha(3f))
    }

    @Test
    fun theSeparatorOnlyShowsOnceTheTitleIsGone() {
        assertEquals(0f, LargeTitle.separatorAlpha(0.2f))
        assertTrue(LargeTitle.separatorAlpha(0.8f) in 0f..1f)
        assertEquals(1f, LargeTitle.separatorAlpha(1f))
    }
}
