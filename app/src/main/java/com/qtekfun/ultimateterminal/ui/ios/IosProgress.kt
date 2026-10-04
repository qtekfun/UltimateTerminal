// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val BarHeight = 4.dp

/** A thin progress bar in the tint of the theme. [fraction] null means it is not known how far along it is. */
@Composable
fun IosProgress(fraction: Float?, modifier: Modifier = Modifier) {
    val colors = IosTheme.colors
    val shape = RoundedCornerShape(BarHeight)
    Box(modifier.fillMaxWidth().height(BarHeight).clip(shape).background(colors.fill)) {
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(BarHeight),
                color = colors.tint,
                trackColor = colors.fill
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
                color = colors.tint,
                trackColor = colors.fill,
                drawStopIndicator = {}
            )
        }
    }
}
