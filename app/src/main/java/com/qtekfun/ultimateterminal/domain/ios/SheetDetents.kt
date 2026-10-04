// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

private const val MEDIUM_FRACTION = 0.5f

/** The large detent stops short of the top, so the page behind still peeks out as in iOS. */
private const val LARGE_FRACTION = 0.94f

/** A height a bottom sheet rests at, as a fraction of the space it may use. */
enum class SheetDetent(val fraction: Float) {
    MEDIUM(MEDIUM_FRACTION),
    LARGE(LARGE_FRACTION)
}

/** Where a released sheet goes. */
sealed interface SheetSnap {
    data class To(val detent: SheetDetent) : SheetSnap

    data object Dismiss : SheetSnap
}

/** Where a dragged bottom sheet settles when the finger lets go. */
object SheetDetents {
    /** How far ahead the release velocity is projected: a flick carries the sheet a little further. */
    private const val PROJECTION_SECONDS = 0.15f

    /** Below this share of the lowest detent the sheet is dismissed instead of resting. */
    private const val DISMISS_SHARE = 0.5f

    /**
     * [heightFraction] is where the sheet is when released and [velocityFractionPerSecond] how fast
     * it moves (positive grows it). It goes to the nearest of [detents] after projecting the
     * velocity, or away if it was thrown below half of the lowest one.
     */
    fun snap(
        heightFraction: Float,
        velocityFractionPerSecond: Float,
        detents: List<SheetDetent> = SheetDetent.entries
    ): SheetSnap {
        require(detents.isNotEmpty()) { "A sheet needs at least one detent" }
        val projected = heightFraction + velocityFractionPerSecond * PROJECTION_SECONDS
        val lowest = detents.minOf { it.fraction }
        return if (projected < lowest * DISMISS_SHARE) {
            SheetSnap.Dismiss
        } else {
            SheetSnap.To(detents.minBy { kotlin.math.abs(it.fraction - projected) })
        }
    }
}
