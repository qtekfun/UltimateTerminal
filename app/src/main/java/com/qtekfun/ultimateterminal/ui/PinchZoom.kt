// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Reports the scale of a two-finger pinch, one factor per movement. It consumes the movement only
 * while two or more fingers are down, so a single finger still reaches the scroll, selection and
 * tap detectors. [onZoom] must be stable (it keys the gesture). Add this after the other pointer
 * modifiers: the last one added sees the events first. Not validated on a device (DECISIONS.md, T11).
 */
fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(onZoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.filter { it.positionChanged() }.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
