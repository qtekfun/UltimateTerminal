// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui.ios

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Where a context menu of [menu] size goes: its top-right corner on the anchor's, as iOS does. If
 * that would leave the window (an anchor in a rail at the left edge), the menu opens beside the
 * anchor instead, and it is always kept inside the [window].
 */
internal fun menuPlacement(anchor: IntRect, window: IntSize, menu: IntSize): IntOffset {
    val preferred = anchor.right - menu.width
    val x = if (preferred < 0) anchor.right else preferred
    return IntOffset(
        x.coerceIn(0, maxOf(0, window.width - menu.width)),
        anchor.top.coerceIn(0, maxOf(0, window.height - menu.height))
    )
}

/** [menuPlacement] for a `Popup`. */
internal object MenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = menuPlacement(anchorBounds, windowSize, popupContentSize)
}
