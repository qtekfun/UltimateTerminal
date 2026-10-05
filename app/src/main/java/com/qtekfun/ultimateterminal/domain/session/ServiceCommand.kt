// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** What a start of the foreground service asks for, by its intent action; no Android types. */
enum class ServiceCommand {
    NEW_SESSION,
    EXIT,
    NONE;

    companion object {
        const val ACTION_NEW_SESSION = "com.qtekfun.ultimateterminal.action.NEW_SESSION"
        const val ACTION_EXIT = "com.qtekfun.ultimateterminal.action.EXIT"

        /** An unknown or missing action (a stale or redelivered intent) asks for nothing. */
        fun of(action: String?): ServiceCommand = when (action) {
            ACTION_NEW_SESSION -> NEW_SESSION
            ACTION_EXIT -> EXIT
            else -> NONE
        }
    }
}
