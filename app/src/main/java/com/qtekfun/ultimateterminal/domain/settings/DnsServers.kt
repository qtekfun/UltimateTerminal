// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import com.qtekfun.ultimateterminal.domain.launch.ResolvConf

/** Why a typed list of DNS servers cannot be used. */
enum class DnsProblem { NOT_AN_ADDRESS, TOO_MANY }

/** The result of reading what the user typed: the servers, or the first thing wrong with it. */
data class DnsParse(val servers: List<String>, val problem: DnsProblem?, val rejected: List<String>)

/**
 * The fallback DNS servers the user may set (SPEC RF-11, Network). They are used only when the
 * device reports none (D-T08b-6). A blank text means "the built-in ones".
 */
object DnsServers {
    private val separators = Regex("[\\s,;]+")

    /** Reads IP literals separated by spaces, commas, semicolons or new lines. */
    fun parse(text: String): DnsParse {
        val tokens = text.split(separators).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return DnsParse(ResolvConf.FALLBACK_SERVERS, null, emptyList())
        // A zone ("fe80::1%wlan0") is dropped by the sanitizer; a typed one is rejected instead.
        val (valid, invalid) = tokens.partition { token -> ResolvConf.sanitize(token) == token }
        val servers = valid.distinct()
        val problem = when {
            invalid.isNotEmpty() -> DnsProblem.NOT_AN_ADDRESS
            servers.size > ResolvConf.MAX_SERVERS -> DnsProblem.TOO_MANY
            else -> null
        }
        return DnsParse(servers.take(ResolvConf.MAX_SERVERS), problem, invalid)
    }

    /** The servers as the text box shows them. */
    fun format(servers: List<String>): String = servers.joinToString(", ")

    fun isDefault(servers: List<String>): Boolean = servers == ResolvConf.FALLBACK_SERVERS
}
