// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.data.proot.ProotLaunch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellStartTest {
    private val inherited = mapOf("ANDROID_ROOT" to "/system", "SECRET" to "no")

    @Test
    fun `the Android shell is what the app has always run`() {
        val start = ShellStart.androidShell("/files", "/cache", inherited)

        assertEquals("/system/bin/sh", start.executable)
        assertEquals(listOf("sh"), start.arguments)
        assertEquals("/files", start.workingDirectory)
        assertTrue("HOME=/files" in start.environment)
        assertTrue("ANDROID_ROOT=/system" in start.environment)
    }

    @Test
    fun `the host environment does not pass unknown variables on`() {
        val start = ShellStart.androidShell("/files", "/cache", inherited)

        assertTrue(start.environment.none { it.startsWith("SECRET=") })
    }

    @Test
    fun `proot runs its own command line and its variables win`() {
        val launch = ProotLaunch(
            command = listOf("/lib/libproot.so", "-r", "/rootfs", "/bin/sh"),
            environment = mapOf("PROOT_LOADER" to "/lib/loader", "TMPDIR" to "/proot-tmp")
        )

        val start = ShellStart.proot(launch, "/files", "/cache", inherited)

        assertEquals("/lib/libproot.so", start.executable)
        assertEquals(launch.command, start.arguments)
        assertEquals("/files", start.workingDirectory)
        assertTrue("PROOT_LOADER=/lib/loader" in start.environment)
        assertTrue("TMPDIR=/proot-tmp" in start.environment)
        assertTrue("ANDROID_ROOT=/system" in start.environment)
        assertEquals(1, start.environment.count { it.startsWith("TMPDIR=") })
    }

    @Test
    fun `the environment is sorted so the result does not depend on map order`() {
        val launch = ProotLaunch(listOf("/p"), mapOf("B" to "2", "A" to "1"))

        val start = ShellStart.proot(launch, "/f", "/c", emptyMap())

        assertEquals(start.environment.sorted(), start.environment.toList())
    }

    @Test
    fun `starts are equal when their contents are, even though the environment is an array`() {
        val one = ShellStart.androidShell("/f", "/c", inherited)
        val same = ShellStart.androidShell("/f", "/c", inherited)
        val other = ShellStart.androidShell("/g", "/c", inherited)

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertNotEquals(one, other)
        assertNotEquals(one, "not a start")
    }
}
