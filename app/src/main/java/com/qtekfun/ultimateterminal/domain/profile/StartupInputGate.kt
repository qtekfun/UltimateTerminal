// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

/**
 * Decides when the start-up command of a profile is typed into its shell (D-FIX-7). Typing at a fixed
 * delay lands before the first prompt is drawn, so the terminal echoes the command twice. The shell
 * is ready when it has written something (its first prompt, or a banner and then the prompt) and
 * then gone quiet for [quietMillis]; if it never does, [maxWaitMillis] after the start the command is
 * typed anyway, so a silent or very noisy shell does not lose it.
 *
 * Pure: the caller feeds it the times (a fake clock in tests) and asks [isReady]; it holds no timer.
 */
class StartupInputGate(
    private val startMillis: Long,
    private val quietMillis: Long = QUIET_MILLIS,
    private val maxWaitMillis: Long = MAX_WAIT_MILLIS
) {
    private var lastOutputMillis: Long? = null

    /** The shell wrote bytes at [nowMillis]. */
    fun onOutput(nowMillis: Long) {
        lastOutputMillis = nowMillis
    }

    /** Whether the command may be typed at [nowMillis]. */
    fun isReady(nowMillis: Long): Boolean {
        val last = lastOutputMillis
        val quiet = last != null && nowMillis - last >= quietMillis
        return quiet || nowMillis - startMillis >= maxWaitMillis
    }

    companion object {
        /** Silence after the last output that is taken as "the prompt is drawn and waiting". */
        const val QUIET_MILLIS = 300L

        /** The longest the command waits, counted from the start of the shell. */
        const val MAX_WAIT_MILLIS = 5_000L
    }
}
