// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BarStyleTest {
    @Test
    fun fromAndroid12TheBarBlursWhatIsBehindIt() {
        val style = BarStyle.forSdk(BarStyle.FIRST_BLUR_SDK)

        assertTrue(style.blurs)
        assertTrue(style.tintAlpha < 1f)
        assertTrue(BarStyle.forSdk(36).blurs)
    }

    @Test
    fun beforeThatTheBarIsAlmostSolidSoItStillReads() {
        val style = BarStyle.forSdk(BarStyle.FIRST_BLUR_SDK - 1)

        assertFalse(style.blurs)
        assertTrue(style.tintAlpha > BarStyle.forSdk(BarStyle.FIRST_BLUR_SDK).tintAlpha)
        assertFalse(BarStyle.forSdk(26).blurs)
    }
}
