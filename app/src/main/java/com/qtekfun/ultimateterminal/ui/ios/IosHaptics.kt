// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The taps iOS gives on its controls, on top of Android's haptic feedback (which the user may turn off). */
class IosHaptics(private val feedback: HapticFeedback) {
    /** A light tick: a segment or row was chosen. */
    fun selection() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A switch went on. */
    fun toggleOn() = feedback.performHapticFeedback(HapticFeedbackType.ToggleOn)

    /** A switch went off. */
    fun toggleOff() = feedback.performHapticFeedback(HapticFeedbackType.ToggleOff)

    /** An action went through. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A menu or sheet opened by a long press. */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberIosHaptics(): IosHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { IosHaptics(feedback) }
}
