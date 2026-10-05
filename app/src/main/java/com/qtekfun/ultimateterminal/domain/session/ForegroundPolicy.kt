// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** What the foreground service does with its notification, as a pure rule (D-NOTIF-2). */
object ForegroundPolicy {
    /** What the service does after handling a command or seeing a new session state. */
    enum class Step { STAY, STOP_AND_REMOVE }

    /**
     * The running count the notification shows, or null when it must not exist: with no running shell
     * the notification is never posted, whoever asks (a late collector, a stale action).
     */
    fun notificationCount(sessions: Sessions): Int? = sessions.runningCount.takeIf { it > 0 }

    /**
     * After a start command has been handled (the service was already promoted, which Android
     * requires within seconds of startForegroundService): stop and remove the notification unless a
     * shell runs. This is also the answer to every later state, so the zero-sessions state itself
     * ends the service.
     */
    fun step(sessions: Sessions): Step =
        if (sessions.needsService) Step.STAY else Step.STOP_AND_REMOVE
}
