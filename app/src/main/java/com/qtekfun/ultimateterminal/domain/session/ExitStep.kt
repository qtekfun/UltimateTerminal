// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** What the "Exit" entry of the menu does first (SPEC RF-16). */
sealed interface ExitStep {
    /** Nothing is running: leave at once. */
    data object ExitNow : ExitStep

    /** [sessions] shells still run and would be killed: ask before leaving. */
    data class Ask(val sessions: Int) : ExitStep
}

/** Exiting asks only when a shell is alive: with none, there is nothing to lose. */
fun exitStep(sessions: Sessions): ExitStep {
    val running = sessions.runningCount
    return if (running > 0) ExitStep.Ask(running) else ExitStep.ExitNow
}
