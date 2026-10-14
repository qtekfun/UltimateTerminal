// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/**
 * Whether a session whose shell just ended closes by itself. A shell that the user ended (`exit`,
 * Ctrl+D, a program that quit) takes its tab or pane with it, and the app stops with the last one.
 * One that fails right away with an error is kept, so its message ("program not found", "could not
 * start") can be read instead of vanishing.
 */
object AutoClose {
    /** A non-zero end within this time of the start is a failed start, not something the user did. */
    const val FAILED_START_MILLIS = 2_000L

    fun shouldClose(status: Int, livedMillis: Long): Boolean = when {
        status == SessionController.START_FAILED -> false
        status != 0 && livedMillis < FAILED_START_MILLIS -> false
        else -> true
    }
}
