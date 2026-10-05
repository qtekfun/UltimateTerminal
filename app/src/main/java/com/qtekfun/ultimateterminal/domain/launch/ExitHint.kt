// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

/** What an exit code of a session that ended at once most likely means, for a clearer message. */
enum class ExitHint {
    /** 127: the shell or program to run was not found. */
    COMMAND_NOT_FOUND,

    /** 126: found but not executable. */
    NOT_EXECUTABLE;

    companion object {
        private const val NOT_FOUND_STATUS = 127
        private const val NOT_EXECUTABLE_STATUS = 126

        fun of(status: Int): ExitHint? = when (status) {
            NOT_FOUND_STATUS -> COMMAND_NOT_FOUND
            NOT_EXECUTABLE_STATUS -> NOT_EXECUTABLE
            else -> null
        }
    }
}
