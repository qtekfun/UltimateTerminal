// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** Text in the iOS type scale. It scales with the user's font size, like any text in the app. */
@Composable
fun IosText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = IosTheme.typography.body,
    color: Color = IosTheme.colors.label,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Unspecified
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
