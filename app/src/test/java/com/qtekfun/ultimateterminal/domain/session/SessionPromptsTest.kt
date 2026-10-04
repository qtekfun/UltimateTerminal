// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionPromptsTest {
    private val fresh = PromptInputs(
        sdkInt = 34,
        hasRunningSession = true,
        notificationsGranted = false,
        notificationsDeclined = false,
        ignoringBatteryOptimizations = false,
        batteryDeclined = false
    )

    @Test
    fun nothingIsAskedBeforeAShellRuns() {
        assertEquals(SessionPrompt.None, nextPrompt(fresh.copy(hasRunningSession = false)))
    }

    @Test
    fun notificationsComeFirstOnAndroid13AndLater() {
        assertEquals(SessionPrompt.Notifications, nextPrompt(fresh))
        assertEquals(
            SessionPrompt.Notifications,
            nextPrompt(fresh.copy(sdkInt = NOTIFICATION_PERMISSION_SDK))
        )
    }

    @Test
    fun notificationsAreNotAskedBeforeAndroid13() {
        assertEquals(
            SessionPrompt.BatteryOptimization,
            nextPrompt(fresh.copy(sdkInt = NOTIFICATION_PERMISSION_SDK - 1))
        )
    }

    @Test
    fun batteryAdviceFollowsOnceNotificationsAreGrantedOrDeclined() {
        assertEquals(
            SessionPrompt.BatteryOptimization,
            nextPrompt(fresh.copy(notificationsGranted = true))
        )
        assertEquals(
            SessionPrompt.BatteryOptimization,
            nextPrompt(fresh.copy(notificationsDeclined = true))
        )
    }

    @Test
    fun nothingIsAskedWhenEverythingIsAnsweredOrAlreadyFine() {
        val answered = fresh.copy(notificationsGranted = true, ignoringBatteryOptimizations = true)
        assertEquals(SessionPrompt.None, nextPrompt(answered))
        assertEquals(
            SessionPrompt.None,
            nextPrompt(fresh.copy(notificationsDeclined = true, batteryDeclined = true))
        )
    }
}
