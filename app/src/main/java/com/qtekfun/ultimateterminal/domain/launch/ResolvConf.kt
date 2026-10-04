// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

/**
 * The `/etc/resolv.conf` a distro gets. Android has none, and a root filesystem's own file points at
 * a resolver that does not exist here, so the app writes one from the device's DNS servers.
 */
object ResolvConf {
    /** glibc reads at most three nameservers. */
    const val MAX_SERVERS = 3

    /**
     * Used only when the device reports no DNS server (a captive or offline network). They are
     * public resolvers run by third parties: see DECISIONS.md, D-T08b-6.
     */
    val FALLBACK_SERVERS = listOf("1.1.1.1", "9.9.9.9")

    private const val IPV4_PARTS = 4
    private const val MAX_OCTET = 255
    private const val MAX_OCTET_DIGITS = 3
    private const val MAX_ADDRESS_LENGTH = 45
    private val IPV6_CHARS = Regex("[0-9a-fA-F:.]+")

    /**
     * One `nameserver` line per usable address; anything that is not an IP literal is dropped. With
     * none left, [fallback] is used (the user's choice in Settings, or the built-in servers), and if
     * that holds nothing usable either, the built-in ones.
     */
    fun render(servers: List<String>, fallback: List<String> = FALLBACK_SERVERS): String {
        val usable = servers.mapNotNull(::sanitize).distinct().take(MAX_SERVERS)
        val chosen = usable.ifEmpty {
            fallback.mapNotNull(::sanitize).distinct().take(MAX_SERVERS).ifEmpty {
                FALLBACK_SERVERS
            }
        }
        return chosen.joinToString("") { "nameserver $it\n" }
    }

    /** An IPv4 or IPv6 literal without a zone (`fe80::1%wlan0` loses `%wlan0`), or null. */
    fun sanitize(raw: String): String? {
        val address = raw.trim().substringBefore('%')
        return address.takeIf { isIpv4(it) || isIpv6(it) }
    }

    private fun isIpv4(text: String): Boolean {
        val parts = text.split('.')
        return parts.size == IPV4_PARTS && parts.all { part ->
            part.length in 1..MAX_OCTET_DIGITS && part.all(Char::isDigit) &&
                part.toInt() <= MAX_OCTET
        }
    }

    // Not a full IPv6 parser: the addresses come from the system, this only keeps junk (a newline, a
    // name, a second directive) out of a file that the resolver reads line by line.
    private fun isIpv6(text: String): Boolean =
        text.length <= MAX_ADDRESS_LENGTH && text.count { it == ':' } >= 2 &&
            IPV6_CHARS.matches(text)
}
