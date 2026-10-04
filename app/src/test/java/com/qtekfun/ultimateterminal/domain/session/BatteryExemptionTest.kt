// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BatteryExemptionTest {
    @Test
    fun `the system dialog for this package comes first`() {
        val first = BatteryExemption.intentsFor("com.example.app").first()

        assertEquals("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", first.action)
        assertEquals("package:com.example.app", first.dataUri)
    }

    @Test
    fun `the general list of exemptions is the way out, without data`() {
        val intents = BatteryExemption.intentsFor("com.example.app")

        assertEquals(2, intents.size)
        assertEquals(
            SettingsIntent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"),
            intents[1]
        )
    }

    @Test
    fun `a blank package name is refused`() {
        assertThrows(IllegalArgumentException::class.java) { BatteryExemption.intentsFor(" ") }
    }
}
