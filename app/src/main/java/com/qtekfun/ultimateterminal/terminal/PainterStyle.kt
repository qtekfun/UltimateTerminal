// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance

/** What [TerminalPainter] needs from the appearance: the spacing and the cursor. */
data class PainterStyle(
    val lineSpacing: Float = TerminalAppearance.DEFAULT_LINE_SPACING,
    val letterSpacing: Float = TerminalAppearance.DEFAULT_LETTER_SPACING,
    val cursorShape: CursorShape = CursorShape.BLOCK,
    val cursorBlink: Boolean = false
) {
    companion object {
        fun of(appearance: TerminalAppearance): PainterStyle = appearance.sanitized().let {
            PainterStyle(it.lineSpacing, it.letterSpacing, it.cursorShape, it.cursorBlink)
        }
    }
}
