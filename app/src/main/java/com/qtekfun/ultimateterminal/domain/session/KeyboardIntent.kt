// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/**
 * Whether the user wants the terminal to have the keyboard, kept apart from whether the input view
 * has the focus at this moment: a layout change (the sidebar opening or closing) may take the focus
 * away without the user having asked for that, and then it is given back (D-T26-7). Pure, so the
 * rules are tested without a screen.
 */
object KeyboardIntent {
    /** What the user asked for: a tap on the terminal shows the keyboard, a covering screen hides it. */
    enum class Request { SHOW, HIDE }

    fun wantedAfter(request: Request): Boolean = request == Request.SHOW

    /**
     * Whether the focus must be given back to the input view: the user wants the keyboard, the window
     * is the one with the focus (otherwise the request is useless, and a dialog may own the keys)
     * and the input view lost the focus.
     */
    fun shouldRestore(wanted: Boolean, hasFocus: Boolean, windowFocused: Boolean): Boolean =
        wanted && !hasFocus && windowFocused
}
