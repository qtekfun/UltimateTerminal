// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.model.Validation

/**
 * Builds the `ssh` command for a saved host.
 *
 * The result is an argument vector, not a shell string: it is run directly, with no shell between
 * the app and `ssh`, so a host name cannot inject a command. The host, the user and the port are
 * validated anyway (a name starting with "-" would be read as an option), and `--` ends the options
 * before the destination.
 */
object SshCommand {
    /** The only place a key file is materialized in the guest; see [SshConnector]. */
    private val keyPathPattern = Regex("""/tmp/\.ut-ssh-[0-9a-f]{16,64}""")
    private const val KEEPALIVE_SECONDS = 30
    private const val KEEPALIVE_MISSES = 3

    fun build(host: SshHost, keyGuestPath: String?): SshResult<List<String>> {
        val address = (Validation.host(host.host) as? Outcome.Success)?.value
        val user = (Validation.user(host.user) as? Outcome.Success)?.value
        val port = (Validation.port(host.port) as? Outcome.Success)?.value
        val invalid = firstInvalid(address, user, port, keyGuestPath)
        return if (invalid == null) {
            // firstInvalid has checked that all three exist.
            val arguments = arguments(
                requireNotNull(address),
                requireNotNull(user),
                requireNotNull(port),
                keyGuestPath
            )
            SshResult.Success(arguments)
        } else {
            SshResult.Failure(SshError.Invalid(invalid))
        }
    }

    /** The name of the first field that is not usable, or null when all are. */
    private fun firstInvalid(
        address: String?,
        user: String?,
        port: Int?,
        keyPath: String?
    ): String? = listOf(
        "host" to (address != null),
        "user" to (user != null),
        "port" to (port != null),
        "keyPath" to (keyPath == null || keyPathPattern.matches(keyPath))
    ).firstOrNull { !it.second }?.first

    private fun arguments(
        address: String,
        user: String,
        port: Int,
        keyPath: String?
    ): List<String> = buildList {
        add("ssh")
        add("-o")
        add("ServerAliveInterval=$KEEPALIVE_SECONDS")
        add("-o")
        add("ServerAliveCountMax=$KEEPALIVE_MISSES")
        add("-p")
        add(port.toString())
        if (keyPath != null) {
            add("-i")
            add(keyPath)
            add("-o")
            add("IdentitiesOnly=yes")
        }
        add("--")
        // ssh wants an IPv6 address without the brackets people write around it.
        add("$user@${address.removePrefix("[").removeSuffix("]")}")
    }
}
