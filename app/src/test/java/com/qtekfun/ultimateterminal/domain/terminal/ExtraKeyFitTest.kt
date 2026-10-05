// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import com.qtekfun.ultimateterminal.domain.appearance.ExtraKeyStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtraKeyFitTest {
    private val labelSp = 13f
    private val phoneWidthDp = 360f

    @Test
    fun theLabelsFollowTheSystemFontOnlyUpToTheCap() {
        assertEquals(1f, ExtraKeyFit.labelFontScale(0.85f))
        assertEquals(1.1f, ExtraKeyFit.labelFontScale(1.1f))
        assertEquals(ExtraKeyFit.MAX_FONT_SCALE, ExtraKeyFit.labelFontScale(2f))
    }

    @Test
    fun everyDefaultKeyFitsItsCellOnAPhoneAtTheLargestFont() {
        val rows = ExtraKeysConfig.default().resolved()
        for (style in ExtraKeyStyle.entries) {
            for (row in rows) {
                val cell = phoneWidthDp / row.size
                for (key in row) {
                    assertTrue(
                        ExtraKeyFit.fits(key.symbol, cell, style.insetDp.toFloat(), labelSp, 2f),
                        "${key.symbol} in $style"
                    )
                }
            }
        }
    }

    @Test
    fun withoutTheCapAWideLabelWouldBeCutAtDoubleFont() {
        // Documents why the cap exists: the same key, drawn at the raw 2.0, overflows its cell.
        val cell = phoneWidthDp / ExtraKeysConfig.default().resolved().first().size
        val raw = 4 * 0.65f * labelSp * 2f

        assertTrue(raw > cell - 2 * ExtraKeyStyle.CLASSIC.insetDp)
        assertFalse(ExtraKeyFit.fits("CTRL CTRL", cell, 3f, labelSp, 2f))
    }
}
