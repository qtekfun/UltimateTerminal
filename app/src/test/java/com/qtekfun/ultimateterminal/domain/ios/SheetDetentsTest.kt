// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SheetDetentsTest {
    @Test
    fun aSheetReleasedNearADetentRestsThere() {
        assertEquals(SheetSnap.To(SheetDetent.MEDIUM), SheetDetents.snap(0.55f, 0f))
        assertEquals(SheetSnap.To(SheetDetent.LARGE), SheetDetents.snap(0.9f, 0f))
    }

    @Test
    fun aFlickUpCarriesTheSheetToTheLargerDetent() {
        assertEquals(SheetSnap.To(SheetDetent.LARGE), SheetDetents.snap(0.55f, 3f))
    }

    @Test
    fun aFlickDownCarriesItToTheSmallerOne() {
        assertEquals(SheetSnap.To(SheetDetent.MEDIUM), SheetDetents.snap(0.8f, -3f))
    }

    @Test
    fun aSheetDraggedFarBelowTheLowestDetentIsDismissed() {
        assertEquals(SheetSnap.Dismiss, SheetDetents.snap(0.2f, 0f))
        assertEquals(SheetSnap.Dismiss, SheetDetents.snap(0.45f, -2f))
    }

    @Test
    fun aSheetJustBelowTheLowestDetentStillRests() {
        assertEquals(SheetSnap.To(SheetDetent.MEDIUM), SheetDetents.snap(0.4f, 0f))
    }

    @Test
    fun onlyTheGivenDetentsAreUsed() {
        val onlyMedium = listOf(SheetDetent.MEDIUM)

        assertEquals(SheetSnap.To(SheetDetent.MEDIUM), SheetDetents.snap(0.95f, 0f, onlyMedium))
    }

    @Test
    fun aSheetWithoutDetentsIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            SheetDetents.snap(0.5f, 0f, emptyList())
        }
    }
}
