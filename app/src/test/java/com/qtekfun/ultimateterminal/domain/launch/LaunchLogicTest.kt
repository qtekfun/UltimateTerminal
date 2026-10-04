// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellQuoteTest {
    @Test
    fun `a plain word is wrapped in single quotes`() {
        assertEquals("'ssh'", ShellQuote.quote("ssh"))
    }

    @Test
    fun `a single quote is closed, escaped and reopened`() {
        assertEquals("'it'\\''s'", ShellQuote.quote("it's"))
    }

    @Test
    fun `shell metacharacters stay inside the quotes`() {
        assertEquals(
            "'a; rm -rf / \$(id) `id` | &'",
            ShellQuote.quote("a; rm -rf / \$(id) `id` | &")
        )
    }

    @Test
    fun `an empty argument is kept as an empty string`() {
        assertEquals("''", ShellQuote.quote(""))
    }

    @Test
    fun `arguments are joined by one space each`() {
        assertEquals(
            "'ssh' '-p' '22' 'host name'",
            ShellQuote.join(listOf("ssh", "-p", "22", "host name"))
        )
    }

    @Test
    fun `a hostile argument cannot end its quote early`() {
        val joined = ShellQuote.join(listOf("echo", "x'; touch /tmp/pwned; echo '"))

        assertEquals("'echo' 'x'\\''; touch /tmp/pwned; echo '\\'''", joined)
    }
}

class GuestUserTest {
    @Test
    fun `ordinary user names are valid`() {
        listOf("root", "dev", "_apt", "user-1", "a_b", "x".repeat(32)).forEach {
            assertTrue(GuestUser.isValid(it), it)
        }
    }

    @Test
    fun `names that could be read as options or break a command are not`() {
        listOf("", "-l", "Root", "1user", "a b", "a;b", "a\nb", "x".repeat(33), "a/b", "\$(id)")
            .forEach { assertFalse(GuestUser.isValid(it), it) }
    }

    @Test
    fun `root and no user are both root, with its own home`() {
        assertTrue(GuestUser.isRoot(null))
        assertTrue(GuestUser.isRoot("root"))
        assertFalse(GuestUser.isRoot("dev"))
        assertEquals("/root", GuestUser.homeOf(null))
        assertEquals("/root", GuestUser.homeOf("root"))
        assertEquals("/home/dev", GuestUser.homeOf("dev"))
    }
}

class ResolvConfTest {
    @Test
    fun `each device server becomes a nameserver line`() {
        assertEquals(
            "nameserver 192.168.1.1\nnameserver 2001:4860:4860::8888\n",
            ResolvConf.render(listOf("192.168.1.1", "2001:4860:4860::8888"))
        )
    }

    @Test
    fun `at most three servers are written, without repeats`() {
        val text = ResolvConf.render(
            listOf("10.0.0.1", "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4")
        )

        assertEquals("nameserver 10.0.0.1\nnameserver 10.0.0.2\nnameserver 10.0.0.3\n", text)
    }

    @Test
    fun `the zone of a link-local address is dropped`() {
        assertEquals("fe80::1", ResolvConf.sanitize("fe80::1%wlan0"))
    }

    @Test
    fun `anything that is not an address cannot reach the file`() {
        listOf(
            "example.com",
            "1.2.3",
            "1.2.3.4.5",
            "256.1.1.1",
            "1.2.3.",
            "",
            "  ",
            "8.8.8.8\nnameserver 6.6.6.6",
            "::g",
            "fe80",
            "a:".repeat(30) + "a"
        ).forEach { raw -> assertNull(ResolvConf.sanitize(raw), raw) }
    }

    @Test
    fun `surrounding spaces of a valid address are ignored`() {
        assertEquals("1.1.1.1", ResolvConf.sanitize("  1.1.1.1 "))
    }

    @Test
    fun `with no usable server the fallback servers are written`() {
        val expected = ResolvConf.FALLBACK_SERVERS.joinToString("") { "nameserver $it\n" }

        assertEquals(expected, ResolvConf.render(emptyList()))
        assertEquals(expected, ResolvConf.render(listOf("example.com", "")))
    }
}
