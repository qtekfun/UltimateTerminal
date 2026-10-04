// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ios

/** How a navigation bar or toolbar is drawn over the content that scrolls under it. */
data class BarStyle(
    /** Opacity of the bar's tint; the rest is the blurred content behind it. */
    val tintAlpha: Float,
    /** Blur radius in dp, 0 when the bar cannot blur what is behind it. */
    val blurRadiusDp: Float
) {
    val blurs: Boolean get() = blurRadiusDp > 0f

    companion object {
        /** The blur effect the translucent bars need (RenderEffect) arrived with Android 12. */
        const val FIRST_BLUR_SDK = 31

        private const val BLURRED_TINT_ALPHA = 0.72f
        private const val SOLID_TINT_ALPHA = 0.97f
        private const val BLUR_RADIUS_DP = 24f

        /** Translucent with a blur from Android 12; before that a nearly solid bar that still reads. */
        fun forSdk(sdkInt: Int): BarStyle = if (sdkInt >= FIRST_BLUR_SDK) {
            BarStyle(BLURRED_TINT_ALPHA, BLUR_RADIUS_DP)
        } else {
            BarStyle(SOLID_TINT_ALPHA, 0f)
        }
    }
}
