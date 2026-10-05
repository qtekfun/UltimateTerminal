// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/**
 * What the screen does when there are no sessions (SPEC RF-13b): the app closes when the last
 * session goes, so a screen with zero tabs is never left open. A cold start (no session yet, none
 * ever seen by this screen) is not that case: the first layout opens the first tab (RF-13).
 */
object StartupTabRule {
    enum class Action { NONE, FINISH }

    /** The screen came back: one that saw sessions and now finds none is stale and finishes. */
    fun onResume(sessionCount: Int, hadSessions: Boolean): Action =
        if (sessionCount == 0 && hadSessions) Action.FINISH else Action.NONE

    /** The number of sessions changed: reaching zero after having had some finishes the screen. */
    fun onSessionsChanged(sessionCount: Int, hadSessions: Boolean): Action =
        if (sessionCount == 0 && hadSessions) Action.FINISH else Action.NONE
}
