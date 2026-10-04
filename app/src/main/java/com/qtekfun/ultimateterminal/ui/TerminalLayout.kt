// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimateterminal.domain.session.TabBarPlacement
import com.qtekfun.ultimateterminal.domain.session.reserveForTabBar
import com.qtekfun.ultimateterminal.domain.session.tabBarPlacement
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.extraKeysHeightPx
import com.qtekfun.ultimateterminal.domain.terminal.reserveBottom
import com.qtekfun.ultimateterminal.domain.terminal.terminalLayoutFor
import com.qtekfun.ultimateterminal.domain.terminal.withTextMargin
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel

/**
 * The tab bar is a row on top of narrow windows and a column beside the terminal on wide ones;
 * either way the grid must not count the space it takes.
 */
internal fun tabBarPlacementOf(windowSize: IntSize, density: Density): TabBarPlacement =
    tabBarPlacement(with(density) { windowSize.width.toDp().value.toInt() })

/**
 * The extra-keys row as it is shown: the configured keys, hidden when the keyboard is. The grid and
 * the row both read this, so the space the row gives back is the space the grid takes.
 */
@Composable
internal fun rememberShownExtraKeys(viewModel: TerminalViewModel): ExtraKeysConfig {
    val configured by viewModel.extraKeys.collectAsStateWithLifecycle()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return configured.shownWith(keyboardVisible)
}

/**
 * Tells the terminal how big its grid is: the window, minus what the system bars, the keyboard, the
 * extra-keys row, the tab bar and the text margin take (T04), whenever any of them changes.
 */
@Composable
internal fun LayoutEffect(
    viewModel: TerminalViewModel,
    painter: TerminalPainter,
    windowSize: IntSize,
    insets: EdgeInsets,
    marginDp: Int
) {
    val density = LocalDensity.current
    val extraKeys = rememberShownExtraKeys(viewModel)
    // The extra-keys row sits above the keyboard, so its height is not terminal area.
    val extraKeysPx = extraKeysHeightPx(extraKeys, with(density) { ExtraKeyRowHeight.roundToPx() })
    val placement = tabBarPlacementOf(windowSize, density)
    val tabBarPx = with(density) {
        (if (placement == TabBarPlacement.Top) TabBarHeight else TabBarSideWidth).roundToPx()
    }
    val textMarginPx = with(density) { marginDp.dp.roundToPx() }
    LaunchedEffect(windowSize, insets, extraKeysPx, placement, tabBarPx, painter, textMarginPx) {
        if (windowSize != IntSize.Zero) {
            viewModel.onLayoutChanged(
                terminalLayoutFor(
                    windowSize.width,
                    windowSize.height,
                    insets.reserveBottom(extraKeysPx).reserveForTabBar(placement, tabBarPx)
                        .withTextMargin(textMarginPx),
                    painter.cellWidth,
                    painter.cellHeight
                )
            )
        }
    }
}

/** What the system bars, the display cutout and the keyboard cover, combined edge by edge. */
@Composable
internal fun coveredEdges(): EdgeInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return WindowInsets.systemBars.toEdgeInsets(density, direction) union
        WindowInsets.displayCutout.toEdgeInsets(density, direction) union
        WindowInsets.ime.toEdgeInsets(density, direction)
}

private fun WindowInsets.toEdgeInsets(density: Density, direction: LayoutDirection) = EdgeInsets(
    left = getLeft(density, direction),
    top = getTop(density),
    right = getRight(density, direction),
    bottom = getBottom(density)
)

internal fun EdgeInsets.toPadding(density: Density) = with(density) {
    PaddingValues(
        start = left.toDp(),
        top = top.toDp(),
        end = right.toDp(),
        bottom = bottom.toDp()
    )
}
