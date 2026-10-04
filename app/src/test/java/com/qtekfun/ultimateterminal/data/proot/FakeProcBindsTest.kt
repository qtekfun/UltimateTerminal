// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FakeProcBindsTest {
    @Test
    fun `the binds map each file to its place under proc, in a stable order`() {
        val binds = fakeProcBinds(mapOf("uptime" to "/f/uptime", "stat" to "/f/stat"))

        assertEquals(
            listOf(ProotBind("/f/stat", "/proc/stat"), ProotBind("/f/uptime", "/proc/uptime")),
            binds
        )
    }

    @Test
    fun `no files, no binds`() {
        assertEquals(emptyList<ProotBind>(), fakeProcBinds(emptyMap()))
    }

    @Test
    fun `the default source provides nothing`() = runTest {
        assertEquals(emptyMap<String, String>(), FakeProcSource.None.hostFiles())
    }
}
