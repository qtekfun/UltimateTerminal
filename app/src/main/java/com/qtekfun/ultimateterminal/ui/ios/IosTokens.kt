// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.ui.unit.dp

/** The 8-point grid of the iOS-style screens. */
object IosSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** Corner radii. */
object IosRadius {
    val control = 8.dp
    val segment = 7.dp
    val card = 10.dp
    val button = 12.dp
    val alert = 14.dp
    val sheet = 20.dp
}

/** Sizes of the controls. Anything a finger must hit is at least 48 dp (CLAUDE.md, accessibility). */
object IosSize {
    val minTouch = 48.dp
    val rowMinHeight = 48.dp
    val navBarHeight = 44.dp
    val hairline = 0.5.dp
    val icon = 22.dp
    val rowIcon = 28.dp
    val switchWidth = 51.dp
    val switchHeight = 31.dp
    val switchThumb = 27.dp
    val switchPadding = 2.dp
    val segmentedHeight = 32.dp
    val searchHeight = 36.dp
    val buttonHeight = 50.dp
    val sheetHandleWidth = 36.dp
    val sheetHandleHeight = 5.dp
    val alertWidth = 270.dp
    val alertButtonHeight = 44.dp
    val menuWidth = 250.dp
    val separatorInset = 16.dp
}
