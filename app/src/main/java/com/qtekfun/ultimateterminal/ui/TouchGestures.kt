// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Waits until the touch that started with [down] ends. True means it was a tap: one finger, lifted
 * without moving past the touch slop and (unless [ignoreConsumed]) without any other detector taking
 * it. A drag in any direction or a second finger is not a tap, as in Android's views: a scroll that
 * is not quite vertical must not count as a tap (a tap clears the selection).
 */
private suspend fun AwaitPointerEventScope.awaitTapEnd(
    down: PointerInputChange,
    pass: PointerEventPass,
    ignoreConsumed: Boolean
): Boolean {
    val slop = viewConfiguration.touchSlop
    var tap: Boolean? = null
    while (tap == null) {
        val event = awaitPointerEvent(pass)
        val change = event.changes.firstOrNull { it.id == down.id }
        tap = when {
            change == null || event.changes.count { it.pressed } > 1 -> false
            !ignoreConsumed && change.isConsumed -> false
            (change.position - down.position).getDistance() > slop -> false
            !change.pressed -> true
            else -> null
        }
    }
    return tap
}

/**
 * A tap: a touch lifted quickly where it went down. It never fires for a scroll, a long press or a
 * pinch, unlike a bare `detectTapGestures`, which only stops when another detector consumes.
 */
internal fun Modifier.onPlainTap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            awaitTapEnd(down, PointerEventPass.Main, ignoreConsumed = false)
        }
        if (tap == true) onTap()
    }
}

/**
 * Reports when a touch becomes more than a tap (it moves past the slop or is held for a long press)
 * and when every finger lifts again. The floating menu hides meanwhile, as it does over a text
 * view. It only watches: it never consumes, so it can go first in the chain.
 */
internal fun Modifier.trackTouchInProgress(onChange: (Boolean) -> Unit): Modifier =
    pointerInput(onChange) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                awaitTapEnd(down, PointerEventPass.Initial, ignoreConsumed = true)
            }
            if (ended == true) return@awaitEachGesture
            onChange(true)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                onChange(false)
            }
        }
    }
