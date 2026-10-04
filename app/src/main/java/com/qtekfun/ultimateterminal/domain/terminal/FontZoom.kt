// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/**
 * The terminal font size as a pinch or a shortcut changes it. A pinch delivers many small scale
 * factors; rounding each one would make slow pinches stick, so the exact size is kept apart and
 * only the shown [sizeSp] is rounded (to half a point) and clamped.
 */
class FontZoom(private val defaultSp: Float = DEFAULT_SP) {
    private var exact = defaultSp.coerceIn(MIN_SP, MAX_SP)

    val sizeSp: Float get() = (exact * ROUNDING).let { Math.round(it) / ROUNDING }

    /** Multiplies the size by a pinch's [factor]; non-positive or non-finite factors are ignored. */
    fun pinch(factor: Float): Float {
        if (factor > 0f && factor.isFinite()) exact = (exact * factor).coerceIn(MIN_SP, MAX_SP)
        return sizeSp
    }

    /** Sets the size directly (a stored size coming back); non-finite values are ignored. */
    fun set(sizeSp: Float): Float {
        if (sizeSp.isFinite()) exact = sizeSp.coerceIn(MIN_SP, MAX_SP)
        return this.sizeSp
    }

    fun zoomIn(): Float = stepBy(STEP_SP)

    fun zoomOut(): Float = stepBy(-STEP_SP)

    fun reset(): Float {
        exact = defaultSp.coerceIn(MIN_SP, MAX_SP)
        return sizeSp
    }

    private fun stepBy(delta: Float): Float {
        exact = (sizeSp + delta).coerceIn(MIN_SP, MAX_SP)
        return sizeSp
    }

    companion object {
        const val DEFAULT_SP = 14f
        const val MIN_SP = 8f
        const val MAX_SP = 40f
        const val STEP_SP = 1f
        private const val ROUNDING = 2f
    }
}
