// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The fallback servers of `resolv.conf` can now be the user's own (T16). */
class ResolvConfFallbackTest {
    private fun lines(vararg servers: String) = servers.joinToString("") { "nameserver $it\n" }

    @Test
    fun theUsersFallbackIsUsedWhenTheDeviceReportsNoServer() {
        assertEquals(lines("9.9.9.9"), ResolvConf.render(emptyList(), listOf("9.9.9.9")))
    }

    @Test
    fun theDevicesServersAlwaysWinOverTheFallback() {
        assertEquals(
            lines("192.168.1.1"),
            ResolvConf.render(listOf("192.168.1.1"), listOf("9.9.9.9"))
        )
    }

    @Test
    fun aFallbackWithNothingUsableGoesBackToTheBuiltInServers() {
        val builtIn = ResolvConf.FALLBACK_SERVERS.joinToString("") { "nameserver $it\n" }

        assertEquals(builtIn, ResolvConf.render(emptyList(), listOf("evil.example", "")))
        assertEquals(builtIn, ResolvConf.render(emptyList(), emptyList()))
    }

    @Test
    fun theFallbackIsCheckedAndCappedLikeTheDevicesServers() {
        val text = ResolvConf.render(
            emptyList(),
            listOf("1.1.1.1", "x", "1.1.1.1", "8.8.8.8", "9.9.9.9", "8.8.4.4")
        )

        assertEquals(lines("1.1.1.1", "8.8.8.8", "9.9.9.9"), text)
    }
}
