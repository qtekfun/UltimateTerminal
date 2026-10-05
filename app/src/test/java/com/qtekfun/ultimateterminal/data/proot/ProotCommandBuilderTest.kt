// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.launch.GuestAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProotCommandBuilderTest {
    private val builder = ProotCommandBuilder("/data/app/lib/arm64", "/data/data/app/tmp")
    private val rootfs = "/data/data/app/files/distros/alpine"

    @Test
    fun runsProotFromTheNativeLibraryDirectory() {
        val launch = builder.build(ProotSession(rootfs))
        assertEquals("/data/app/lib/arm64/libproot.so", launch.command.first())
    }

    @Test
    fun pointsProotAtItsLoaderAndTmpDir() {
        val env = builder.build(ProotSession(rootfs)).environment
        assertEquals("/data/app/lib/arm64/libproot-loader.so", env["PROOT_LOADER"])
        assertEquals("/data/data/app/tmp", env["PROOT_TMP_DIR"])
    }

    @Test
    fun emulatesHardLinksAndKillsTraceesOnExit() {
        val command = builder.build(ProotSession(rootfs)).command
        assertTrue("--link2symlink" in command)
        assertTrue("--kill-on-exit" in command)
    }

    @Test
    fun setsRootfsBindsAndWorkingDirectory() {
        val command = builder.build(
            ProotSession(rootfs, binds = listOf(ProotBind("/sdcard", "/root/storage")))
        ).command
        assertEquals(rootfs, command[command.indexOf("-r") + 1])
        assertEquals("/sdcard:/root/storage", command[command.indexOf("-b") + 1])
        assertEquals("/root", command[command.indexOf("-w") + 1])
    }

    @Test
    fun bindsDevProcAndSysByDefault() {
        val command = builder.build(ProotSession(rootfs)).command
        listOf("/dev:/dev", "/proc:/proc", "/sys:/sys").forEach { assertTrue(it in command) }
    }

    @Test
    fun fakesRootByDefaultAndCanBeTurnedOff() {
        assertTrue("-0" in builder.build(ProotSession(rootfs)).command)
        assertFalse("-0" in builder.build(ProotSession(rootfs, fakeRoot = false)).command)
    }

    @Test
    fun startsTheShellWithACleanGuestEnvironment() {
        val command = builder.build(ProotSession(rootfs, term = "xterm")).command
        val start = command.indexOf("/usr/bin/env")
        assertEquals(
            listOf(
                "/usr/bin/env",
                "-i",
                "HOME=/root",
                "TERM=xterm",
                "LANG=C.UTF-8",
                "PATH=${ProotCommandBuilder.GUEST_PATH}",
                "/bin/sh",
                "-l"
            ),
            command.subList(start, command.size)
        )
    }

    @Test
    fun nonRootSessionsGetTheirOwnHome() {
        val command = builder.build(ProotSession(rootfs, fakeRoot = false)).command
        assertTrue("HOME=/home" in command)
    }

    @Test
    fun seccompIsOnUnlessDisabled() {
        assertFalse("PROOT_NO_SECCOMP" in builder.build(ProotSession(rootfs)).environment)
        val env = builder.build(ProotSession(rootfs, disableSeccomp = true)).environment
        assertEquals("1", env["PROOT_NO_SECCOMP"])
    }

    private val dev = GuestAccount("dev", 1000, 1001, "/home/dev", "/bin/bash")

    @Test
    fun `a named user runs its login shell under proot's identity switch, without su`() {
        val command = builder.build(ProotSession(rootfs, account = dev)).command

        assertEquals("1000:1001", command[command.indexOf("-i") + 1])
        assertFalse("-0" in command)
        assertFalse("su" in command)
        val start = command.indexOf("/usr/bin/env")
        assertEquals(
            listOf(
                "/usr/bin/env",
                "-i",
                "HOME=/home/dev",
                "USER=dev",
                "LOGNAME=dev",
                "SHELL=/bin/bash",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "PATH=${ProotCommandBuilder.GUEST_PATH}",
                "/bin/bash",
                "-l"
            ),
            command.subList(start, command.size)
        )
    }

    @Test
    fun `root as a named user is the same as no user`() {
        assertEquals(
            builder.build(ProotSession(rootfs)).command,
            builder.build(
                ProotSession(rootfs, account = GuestAccount("root", 0, 0, "/root", "/bin/sh"))
            ).command
        )
    }

    @Test
    fun `a command runs as an argument list for root`() {
        val command = builder.build(
            ProotSession(rootfs, command = listOf("ssh", "-p", "22", "host"))
        ).command

        assertEquals(listOf("ssh", "-p", "22", "host"), command.takeLast(4))
        assertFalse("/bin/sh" in command)
    }

    @Test
    fun `a command runs as proot's identity whatever the user is`() {
        val command = builder.build(
            ProotSession(rootfs, account = dev, command = listOf("ssh", "host"))
        ).command

        assertEquals(listOf("ssh", "host"), command.takeLast(2))
        assertTrue("-0" in command)
        assertFalse("-i" in command.take(command.indexOf("/usr/bin/env")))
    }

    @Test
    fun `seccomp can be turned off through the environment`() {
        val env = builder.build(ProotSession(rootfs, disableSeccomp = true)).environment

        assertEquals("1", env["PROOT_NO_SECCOMP"])
    }
}
