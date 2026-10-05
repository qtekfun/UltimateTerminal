// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntSize
import com.qtekfun.ultimateterminal.domain.session.PaneArea
import com.qtekfun.ultimateterminal.terminal.TerminalPainter
import com.qtekfun.ultimateterminal.terminal.TerminalViewModel

/** The thickness of the divider and the height of the header strip, in pixels. */
internal class PaneStrips(val dividerPx: Int, val headerPx: Int)

/** Tells the pane controller how big the area is, which the pane layout and the ptys follow. */
@Composable
internal fun ReportArea(
    viewModel: TerminalViewModel,
    painter: TerminalPainter,
    areaSize: IntSize,
    strips: PaneStrips
) {
    LaunchedEffect(areaSize, painter, strips.dividerPx, strips.headerPx) {
        if (areaSize != IntSize.Zero) {
            viewModel.panes.onArea(
                PaneArea(
                    areaSize.width,
                    areaSize.height,
                    painter.cellWidth,
                    painter.cellHeight,
                    strips.dividerPx,
                    strips.headerPx
                )
            )
        }
    }
}
