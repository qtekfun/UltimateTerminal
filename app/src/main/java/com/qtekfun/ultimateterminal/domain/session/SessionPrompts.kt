// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** What the app asks the user for, once, in the context of starting a shell. */
enum class SessionPrompt { None, Notifications, BatteryOptimization }

data class PromptInputs(
    val sdkInt: Int,
    val hasRunningSession: Boolean,
    val notificationsGranted: Boolean,
    val notificationsDeclined: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val batteryDeclined: Boolean
)

/** The notification permission exists from Android 13 (API 33). */
const val NOTIFICATION_PERMISSION_SDK = 33

/**
 * The next thing to ask. Nothing is asked before a shell runs, and what the user declined is not
 * asked again in this launch. The battery advice comes after the notification question, so the
 * user never sees two dialogs at once.
 */
fun nextPrompt(inputs: PromptInputs): SessionPrompt = when {
    !inputs.hasRunningSession -> SessionPrompt.None

    inputs.sdkInt >= NOTIFICATION_PERMISSION_SDK &&
        !inputs.notificationsGranted &&
        !inputs.notificationsDeclined -> SessionPrompt.Notifications

    !inputs.ignoringBatteryOptimizations &&
        !inputs.batteryDeclined -> SessionPrompt.BatteryOptimization

    else -> SessionPrompt.None
}
