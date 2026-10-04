// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FontZoomTest {
    @Test
    fun startsAtTheDefault() {
        assertEquals(FontZoom.DEFAULT_SP, FontZoom().sizeSp)
    }

    @Test
    fun stepsMoveByOnePointAndStopAtTheLimits() {
        val zoom = FontZoom()
        assertEquals(15f, zoom.zoomIn())
        assertEquals(14f, zoom.zoomOut())
        repeat(100) { zoom.zoomIn() }
        assertEquals(FontZoom.MAX_SP, zoom.sizeSp)
        repeat(100) { zoom.zoomOut() }
        assertEquals(FontZoom.MIN_SP, zoom.sizeSp)
    }

    @Test
    fun aSlowPinchStillAccumulates() {
        val zoom = FontZoom()
        repeat(20) { zoom.pinch(1.01f) }
        // 14 * 1.01^20 = 17.08, rounded to half a point.
        assertEquals(17f, zoom.sizeSp)
    }

    @Test
    fun aPinchIsClamped() {
        val zoom = FontZoom()
        assertEquals(FontZoom.MAX_SP, zoom.pinch(10f))
        assertEquals(FontZoom.MIN_SP, zoom.pinch(0.001f))
    }

    @Test
    fun badFactorsAreIgnored() {
        val zoom = FontZoom()
        assertEquals(14f, zoom.pinch(0f))
        assertEquals(14f, zoom.pinch(-2f))
        assertEquals(14f, zoom.pinch(Float.NaN))
        assertEquals(14f, zoom.pinch(Float.POSITIVE_INFINITY))
    }

    @Test
    fun resetGoesBackToTheDefaultAndAnOutOfRangeDefaultIsClamped() {
        val zoom = FontZoom()
        zoom.pinch(2f)
        assertEquals(14f, zoom.reset())
        assertEquals(FontZoom.MAX_SP, FontZoom(defaultSp = 99f).sizeSp)
    }

    @Test
    fun theSizeIsRoundedToHalfPoints() {
        val zoom = FontZoom()
        assertEquals(14.5f, zoom.pinch(1.04f))
    }
}
