// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

/**
 * The large title of an iOS-style screen: it sits under the navigation bar and, as the content
 * scrolls, shrinks away while the bar's own small title fades in.
 */
object LargeTitle {
    /** Where in the collapse the small title starts to appear: the large one is half gone. */
    private const val SMALL_TITLE_START = 0.5f

    /** How far the collapse has gone, 0 (large title fully shown) to 1 (collapsed). */
    fun collapseFraction(scrollPx: Float, collapseDistancePx: Float): Float =
        if (collapseDistancePx <= 0f) 1f else (scrollPx / collapseDistancePx).coerceIn(0f, 1f)

    /** The large title fades out over the whole collapse. */
    fun largeTitleAlpha(fraction: Float): Float = 1f - fraction.coerceIn(0f, 1f)

    /** The small title of the bar appears during the second half of the collapse. */
    fun smallTitleAlpha(fraction: Float): Float = (
        (
            fraction.coerceIn(
                0f,
                1f
            ) - SMALL_TITLE_START
            ) / (1f - SMALL_TITLE_START)
        ).coerceIn(0f, 1f)

    /** The bar's separator shows once the content has really moved under it. */
    fun separatorAlpha(fraction: Float): Float = if (fraction >=
        1f
    ) {
        1f
    } else {
        smallTitleAlpha(fraction)
    }
}
