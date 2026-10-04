// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.model.SshHost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SshCommandTest {
    private val host = SshHost(name = "web", host = "example.com", user = "deploy")
    private val keyPath = "/tmp/.ut-ssh-0123456789abcdef"

    private fun command(h: SshHost, key: String? = null): List<String> =
        (SshCommand.build(h, key) as SshResult.Success).value

    private fun failure(h: SshHost, key: String? = null): SshError =
        (SshCommand.build(h, key) as SshResult.Failure).error

    @Test
    fun aPlainHostGetsTheDefaultPortAndKeepalives() {
        assertEquals(
            listOf(
                "ssh", "-o", "ServerAliveInterval=30", "-o", "ServerAliveCountMax=3",
                "-p", "22", "--", "deploy@example.com"
            ),
            command(host)
        )
    }

    @Test
    fun aKeyAddsTheIdentityAndRefusesOtherIdentities() {
        val args = command(host, keyPath)
        val at = args.indexOf("-i")
        assertEquals(keyPath, args[at + 1])
        assertEquals("IdentitiesOnly=yes", args[args.indexOf("IdentitiesOnly=yes")])
        assertEquals("--", args[args.size - 2])
    }

    @Test
    fun theDestinationIsAlwaysTheLastArgumentAfterTheEndOfOptions() {
        val args = command(host.copy(port = 2222), keyPath)
        assertEquals("--", args[args.size - 2])
        assertEquals("deploy@example.com", args.last())
        assertEquals("2222", args[args.indexOf("-p") + 1])
    }

    @Test
    fun anIpv6AddressLosesItsBrackets() {
        assertEquals("deploy@::1", command(host.copy(host = "[::1]")).last())
        assertEquals("deploy@fe80::1%wlan0", command(host.copy(host = "fe80::1%wlan0")).last())
    }

    @Test
    fun aHostThatCouldBeReadAsAnOptionIsRefused() {
        assertEquals(SshError.Invalid("host"), failure(host.copy(host = "-oProxyCommand=touch /x")))
        assertEquals(SshError.Invalid("host"), failure(host.copy(host = "-v")))
    }

    @Test
    fun shellMetacharactersInTheHostAreRefused() {
        listOf(
            "a;b", "a b", "a&&b", "a|b", "$(id)", "`id`", "a\nb",
            "a'b", "a\"b", "a>b", "a<b", "a*b", "a\\b", ""
        ).forEach { bad ->
            assertEquals(SshError.Invalid("host"), failure(host.copy(host = bad)), "host: $bad")
        }
    }

    @Test
    fun aUserThatCouldBeReadAsAnOptionOrInjectIsRefused() {
        listOf(
            "-oProxyCommand=x",
            "a b",
            "a;b",
            "a@b",
            "$(id)",
            "`id`",
            "a\nb",
            ""
        ).forEach { bad ->
            assertEquals(SshError.Invalid("user"), failure(host.copy(user = bad)), "user: $bad")
        }
    }

    @Test
    fun aPortOutsideTheValidRangeIsRefused() {
        listOf(0, -1, 65_536, Int.MAX_VALUE).forEach { bad ->
            assertEquals(SshError.Invalid("port"), failure(host.copy(port = bad)), "port: $bad")
        }
        assertEquals("65535", command(host.copy(port = 65_535))[command(host).indexOf("-p") + 1])
    }

    @Test
    fun onlyTheMaterializedKeyLocationIsAcceptedAsAKeyPath() {
        listOf(
            "/etc/passwd", "../../etc/passwd", "/tmp/../etc/passwd", "/tmp/.ut-ssh-xyz",
            "/tmp/.ut-ssh-0123456789abcdef; id",
            "/tmp/.ut-ssh-0123456789abcdef\n-oProxyCommand=x", "-i", "",
            " /tmp/.ut-ssh-0123456789abcdef"
        ).forEach { bad ->
            assertEquals(SshError.Invalid("keyPath"), failure(host, bad), "key: $bad")
        }
    }

    @Test
    fun noArgumentIsEverBuiltFromAShellString() {
        val args = command(host.copy(name = "a; rm -rf /"), keyPath)
        // The name is only a label: it never reaches the command line.
        assertTrue(args.none { "rm" in it })
    }
}
