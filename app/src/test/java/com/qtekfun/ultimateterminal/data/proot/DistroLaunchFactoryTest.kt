// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.ssh.SshLaunchPlan
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DistroLaunchFactoryTest {
    private val distro = Distro(
        id = 1,
        name = "alpine",
        type = DistroType.ALPINE,
        release = "3.22",
        directory = (FsPath.of("distros/abc") as Outcome.Success).value,
        defaultUser = "root",
        state = DistroState.READY,
        sizeBytes = 1,
        installedAt = Instant.EPOCH,
        isDefault = true
    )
    private val builder = ProotCommandBuilder("/data/app/lib/arm64", "/data/cache/proot-tmp")
    private val storage = InMemoryFileSystemRepository(rootPath = "/data/files/storage")
    private var resolvConf: String? = "/data/files/resolv.conf"
    private val dns = ResolvConfSource { resolvConf }

    @Test
    fun theCommandRunsTheGuestCommandInsideTheDistrosRootfs() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var cleaned = 0
        val plan = SshLaunchPlan(distro, listOf("ssh", "--", "u@h")) { cleaned++ }
        val launch = DistroLaunchFactory(builder, storage, dns, scope).create(plan)
        val rootAt = launch.command.indexOf("-r")
        assertEquals("/data/files/storage/distros/abc", launch.command[rootAt + 1])
        assertEquals(listOf("ssh", "--", "u@h"), launch.command.takeLast(3))
        assertTrue(launch.command.first().endsWith("libproot.so"))
        assertEquals("/data/app/lib/arm64/libproot-loader.so", launch.environment["PROOT_LOADER"])
        assertEquals(0, cleaned)
    }

    @Test
    fun closingTheSessionRunsThePlansCleanup() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var cleaned = 0
        val plan = SshLaunchPlan(distro, listOf("ssh", "u@h")) { cleaned++ }
        val launch = DistroLaunchFactory(builder, storage, dns, scope).create(plan)
        launch.onClosed()
        scope.advanceUntilIdle()
        assertEquals(1, cleaned)
    }

    @Test
    fun theLaunchDoesNotPrintItsCommandLine() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val plan = SshLaunchPlan(distro, listOf("ssh", "-i", "/tmp/.ut-ssh-key", "u@h")) {}
        val launch = DistroLaunchFactory(builder, storage, dns, scope).create(plan)
        assertFalse(launch.toString().contains(".ut-ssh"))
    }

    @Test
    fun theDevicesResolverIsBoundOverTheDistrosOwn() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val plan = SshLaunchPlan(distro, listOf("ssh", "u@h")) {}

        val launch = DistroLaunchFactory(builder, storage, dns, scope).create(plan)

        assertTrue("/data/files/resolv.conf:/etc/resolv.conf" in launch.command)
    }

    @Test
    fun withoutAResolverFileTheConnectionStillStartsWithoutTheBind() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        resolvConf = null
        val plan = SshLaunchPlan(distro, listOf("ssh", "u@h")) {}

        val launch = DistroLaunchFactory(builder, storage, dns, scope).create(plan)

        assertFalse(launch.command.any { it.endsWith(":/etc/resolv.conf") })
        assertEquals(listOf("ssh", "u@h"), launch.command.takeLast(2))
    }
}
