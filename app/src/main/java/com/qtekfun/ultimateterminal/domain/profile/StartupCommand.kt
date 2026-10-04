// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

/**
 * The command a pane types into its shell when it opens. It is one plain line: no line breaks (that
 * would run several commands from a field meant for one), no control characters (a Tab would ask the
 * shell to complete, an Escape could drive it), and a sane length.
 */
object StartupCommand {
    /** Longest start-up command, in characters. */
    const val MAX_LENGTH = 1_000

    /** What the Enter key sends to a terminal. */
    const val ENTER = "\r"

    sealed interface Check {
        /** Fine as it is: [text] is the trimmed command, null when there is none. */
        data class Valid(val text: String?) : Check

        data class Invalid(val fault: StartupCommandFault) : Check
    }

    /** Blank means "no command". */
    fun check(raw: String?): Check {
        val text = raw?.trim().orEmpty()
        val fault = when {
            text.isEmpty() -> null
            text.any { it == '\n' || it == '\r' } -> StartupCommandFault.MULTIPLE_LINES
            text.any { it.isISOControl() } -> StartupCommandFault.CONTROL_CHARACTER
            text.length > MAX_LENGTH -> StartupCommandFault.TOO_LONG
            else -> null
        }
        return if (fault != null) Check.Invalid(fault) else Check.Valid(text.ifEmpty { null })
    }

    /** What to type for [text]: the command followed by Enter. */
    fun inputFor(text: String): String = text + ENTER
}
