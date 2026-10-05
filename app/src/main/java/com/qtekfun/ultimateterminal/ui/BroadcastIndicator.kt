// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.ios.ColorAdjust
import com.qtekfun.ultimateterminal.ui.ios.IosSpacing
import com.qtekfun.ultimateterminal.ui.ios.IosText
import com.qtekfun.ultimateterminal.ui.ios.IosTheme

private val IndicatorTouchHeight = 48.dp
private val IndicatorCorner = 14.dp

/**
 * The warning that what is typed reaches several panes (SPEC RF-12): it is red, always over the
 * terminal while the broadcast lasts, and a tap on it stops the broadcast. Typing into servers by
 * surprise is the risk of this feature, so the indicator is not optional (D-T12b-5).
 */
@Composable
internal fun BroadcastIndicator(paneCount: Int, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = IosTheme.colors
    val text = pluralStringResource(R.plurals.broadcast_indicator, paneCount, paneCount)
    val description = stringResource(R.string.broadcast_indicator_stop, text)
    Box(
        modifier
            .heightIn(min = IndicatorTouchHeight)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onStop),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .padding(horizontal = IosSpacing.sm)
                .background(colors.destructive, RoundedCornerShape(IndicatorCorner))
                .padding(horizontal = IosSpacing.sm, vertical = IosSpacing.xs)
        ) {
            IosText(
                text,
                color = Color(ColorAdjust.readableOn(colors.destructive.toArgb())),
                maxLines = 1
            )
        }
    }
}
