// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsServersTest {
    @Test
    fun readsAddressesSeparatedByAnyMixOfSeparators() {
        val parsed = DnsServers.parse("1.1.1.1, 8.8.8.8;  9.9.9.9")

        assertEquals(listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"), parsed.servers)
        assertNull(parsed.problem)
    }

    @Test
    fun readsIpv6AndKeepsOrderAndDropsRepeats() {
        val parsed = DnsServers.parse("2606:4700:4700::1111\n1.1.1.1 1.1.1.1")

        assertEquals(listOf("2606:4700:4700::1111", "1.1.1.1"), parsed.servers)
        assertNull(parsed.problem)
    }

    @Test
    fun blankMeansTheBuiltInServers() {
        for (text in listOf("", "   ", " , ; ")) {
            val parsed = DnsServers.parse(text)

            assertTrue(DnsServers.isDefault(parsed.servers))
            assertNull(parsed.problem)
        }
    }

    @Test
    fun aNameOrAJunkTokenIsRejectedAndReported() {
        val parsed = DnsServers.parse("1.1.1.1 dns.google 999.1.1.1 8.8.8.8")

        assertEquals(DnsProblem.NOT_AN_ADDRESS, parsed.problem)
        assertEquals(listOf("dns.google", "999.1.1.1"), parsed.rejected)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), parsed.servers)
    }

    @Test
    fun aZoneTypedByHandIsRejectedNotSilentlyDropped() {
        val parsed = DnsServers.parse("fe80::1%wlan0")

        assertEquals(DnsProblem.NOT_AN_ADDRESS, parsed.problem)
    }

    @Test
    fun moreThanThreeServersIsAProblemAndKeepsTheFirstThree() {
        val parsed = DnsServers.parse("1.1.1.1 8.8.8.8 9.9.9.9 8.8.4.4")

        assertEquals(DnsProblem.TOO_MANY, parsed.problem)
        assertEquals(listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"), parsed.servers)
    }

    @Test
    fun formatAndParseAreInverse() {
        val servers = listOf("9.9.9.9", "2620:fe::fe")

        assertEquals(servers, DnsServers.parse(DnsServers.format(servers)).servers)
        assertFalse(DnsServers.isDefault(servers))
    }
}
