// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome

/** Validation shared by every named item (distros, profiles, layouts, hosts). */
object Validation {
    const val MAX_NAME_LENGTH = 64
    const val MIN_PORT = 1
    const val MAX_PORT = 65_535

    private val hostPattern = Regex("""[A-Za-z0-9._:%\[\]-]+""")
    private val userPattern = Regex("""[A-Za-z0-9._][A-Za-z0-9._-]*""")

    /** Trims [raw] and checks it is a printable name of at most [MAX_NAME_LENGTH] characters. */
    fun name(raw: String): Outcome<String> {
        val name = raw.trim()
        val valid = name.isNotEmpty() &&
            name.length <= MAX_NAME_LENGTH &&
            name.none { it.isISOControl() }
        return if (valid) Outcome.Success(name) else Outcome.Failure(DomainError.InvalidName(raw))
    }

    /**
     * A host name or address that is safe to pass to `ssh`: it cannot start with "-" (which
     * would be read as an option) and cannot contain spaces or shell characters.
     */
    fun host(raw: String): Outcome<String> {
        val host = raw.trim()
        val valid = hostPattern.matches(host) && !host.startsWith("-")
        return if (valid) {
            Outcome.Success(
                host
            )
        } else {
            Outcome.Failure(DomainError.InvalidValue("host"))
        }
    }

    /** A user name that cannot be read as an `ssh` option. */
    fun user(raw: String): Outcome<String> {
        val user = raw.trim()
        return if (userPattern.matches(user)) {
            Outcome.Success(user)
        } else {
            Outcome.Failure(DomainError.InvalidValue("user"))
        }
    }

    fun port(port: Int): Outcome<Int> = if (port in MIN_PORT..MAX_PORT) {
        Outcome.Success(port)
    } else {
        Outcome.Failure(DomainError.InvalidValue("port"))
    }
}
