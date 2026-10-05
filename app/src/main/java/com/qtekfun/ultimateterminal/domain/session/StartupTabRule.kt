// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/**
 * What the screen does when there are no sessions (SPEC RF-13b): a terminal screen with zero tabs
 * must never be reachable, whichever way the last session went away.
 */
object StartupTabRule {
    enum class Action { NONE, OPEN_DEFAULT_TAB, FINISH }

    /**
     * The screen came to the foreground (also a cold start). With no session, a tab of the default
     * distro opens by itself; this covers an activity kept in recents after the sessions were closed
     * from the notification, while it was stopped and could not react.
     */
    fun onResume(sessionCount: Int): Action =
        if (sessionCount == 0) Action.OPEN_DEFAULT_TAB else Action.NONE

    /**
     * The number of sessions changed. The user closing the last tab while looking at the screen
     * closes the app, as before (the task is removed, so the next launch is a cold start). Anything
     * seen while the screen is not resumed does not finish it: the next resume opens a fresh tab.
     */
    fun onSessionsChanged(sessionCount: Int, hadSessions: Boolean, resumed: Boolean): Action =
        if (sessionCount == 0 && hadSessions && resumed) Action.FINISH else Action.NONE
}
