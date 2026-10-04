// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.qtekfun.ultimateterminal.R

/** Inter (SIL OFL-1.1), bundled unmodified: the free typeface the iOS-style screens use instead of SF Pro. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** The roles of iOS's type scale, from Large Title (34) to Caption (12). */
enum class IosTextRole {
    LARGE_TITLE,
    TITLE1,
    TITLE2,
    TITLE3,
    HEADLINE,
    BODY,
    CALLOUT,
    SUBHEADLINE,
    FOOTNOTE,
    CAPTION
}

/** The text styles of iOS's type scale. */
@Immutable
class IosTypography(private val styles: Map<IosTextRole, TextStyle>) {
    val largeTitle: TextStyle get() = styles.getValue(IosTextRole.LARGE_TITLE)
    val title1: TextStyle get() = styles.getValue(IosTextRole.TITLE1)
    val title2: TextStyle get() = styles.getValue(IosTextRole.TITLE2)
    val title3: TextStyle get() = styles.getValue(IosTextRole.TITLE3)
    val headline: TextStyle get() = styles.getValue(IosTextRole.HEADLINE)
    val body: TextStyle get() = styles.getValue(IosTextRole.BODY)
    val callout: TextStyle get() = styles.getValue(IosTextRole.CALLOUT)
    val subheadline: TextStyle get() = styles.getValue(IosTextRole.SUBHEADLINE)
    val footnote: TextStyle get() = styles.getValue(IosTextRole.FOOTNOTE)
    val caption: TextStyle get() = styles.getValue(IosTextRole.CAPTION)

    companion object {
        private fun style(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp) =
            TextStyle(
                fontFamily = InterFamily,
                fontSize = size.sp,
                lineHeight = line.sp,
                fontWeight = weight,
                letterSpacing = tracking
            )

        val Default = IosTypography(
            mapOf(
                IosTextRole.LARGE_TITLE to
                    style(size = 34, line = 41, weight = FontWeight.Bold, tracking = (-0.4).sp),
                IosTextRole.TITLE1 to
                    style(size = 28, line = 34, weight = FontWeight.Bold, tracking = (-0.3).sp),
                IosTextRole.TITLE2 to
                    style(size = 22, line = 28, weight = FontWeight.Bold, tracking = (-0.2).sp),
                IosTextRole.TITLE3 to style(size = 20, line = 25, weight = FontWeight.SemiBold),
                IosTextRole.HEADLINE to style(size = 17, line = 22, weight = FontWeight.SemiBold),
                IosTextRole.BODY to style(size = 17, line = 22, weight = FontWeight.Normal),
                IosTextRole.CALLOUT to style(size = 16, line = 21, weight = FontWeight.Normal),
                IosTextRole.SUBHEADLINE to style(size = 15, line = 20, weight = FontWeight.Normal),
                IosTextRole.FOOTNOTE to style(size = 13, line = 18, weight = FontWeight.Normal),
                IosTextRole.CAPTION to style(size = 12, line = 16, weight = FontWeight.Normal)
            )
        )
    }
}
