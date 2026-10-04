// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.launch.LaunchNotice
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem
import com.qtekfun.ultimateterminal.domain.launch.ShellRequest
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageMounts
import com.qtekfun.ultimateterminal.domain.storage.StorageDegradation
import com.qtekfun.ultimateterminal.fakes.FakeDistroRepository
import com.qtekfun.ultimateterminal.fakes.FakeSettingsRepository
import com.qtekfun.ultimateterminal.fakes.FakeSharedStorageAccess
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeRuntime(var binaries: Boolean = true, var tmpDir: String? = "/cache/proot-tmp") :
    ProotRuntime {
    override val nativeLibraryDir = "/data/app/lib/arm64"

    override fun hasBinaries() = binaries

    override fun prepareTmpDir() = tmpDir
}

private class FakeDns(var file: String? = "/files/resolv.conf") : ResolvConfSource {
    var calls = 0

    override suspend fun hostFile(): String? {
        calls++
        return file
    }
}

class ProotSessionPlannerTest {
    private val distros = FakeDistroRepository()
    private val fileSystem = InMemoryFileSystemRepository()
    private val settings = FakeSettingsRepository()
    private val access = FakeSharedStorageAccess()
    private val runtime = FakeRuntime()
    private val dns = FakeDns()
    private val planner = ProotSessionPlanner(
        distros,
        fileSystem,
        settings,
        SharedStorageMounts(fileSystem, access),
        runtime,
        dns
    )

    private fun path(raw: String) = checkNotNull(FsPath.of(raw).getOrNull())

    /** A distro in the given state; a ready one also gets its root filesystem on disk. */
    private suspend fun install(
        state: DistroState = DistroState.READY,
        user: String = "root",
        withRootfs: Boolean = true,
        name: String = "Alpine"
    ): Distro {
        val added = distros.add(
            NewDistro(name, DistroType.ALPINE, "3.20", path("distros/a1"), user)
        ) as Outcome.Success
        distros.updateState(added.value.id, state)
        if (withRootfs) fileSystem.createDirectories(path("distros/a1/rootfs"))
        return checkNotNull(distros.get(added.value.id))
    }

    private fun inDistro(plan: LaunchPlan) = plan as LaunchPlan.InDistro

    @Test
    fun `a tab with no distro and no command opens the Android shell`() = runTest {
        assertEquals(LaunchPlan.AndroidShell, planner.plan(ShellRequest(null)))
    }

    @Test
    fun `a ready distro starts proot on its root filesystem`() = runTest {
        val distro = install()

        val plan = inDistro(planner.plan(ShellRequest(distro.id)))

        val command = plan.launch.command
        assertEquals("/data/app/lib/arm64/libproot.so", command.first())
        assertEquals("/storage/distros/a1/rootfs", command[command.indexOf("-r") + 1])
        assertEquals("Alpine", plan.distroName)
        assertEquals(
            "/data/app/lib/arm64/libproot-loader.so",
            plan.launch.environment["PROOT_LOADER"]
        )
        assertEquals("/cache/proot-tmp", plan.launch.environment["PROOT_TMP_DIR"])
        assertEquals(listOf("/bin/sh", "-l"), command.takeLast(2))
        assertNull(plan.notice)
    }

    @Test
    fun `root gets root's home as the working directory`() = runTest {
        val distro = install()

        val command = inDistro(planner.plan(ShellRequest(distro.id))).launch.command

        assertEquals("/root", command[command.indexOf("-w") + 1])
        assertTrue("-0" in command)
        assertTrue("HOME=/root" in command)
    }

    @Test
    fun `another user goes through su with a login shell`() = runTest {
        val distro = install(user = "dev")

        val command = inDistro(planner.plan(ShellRequest(distro.id))).launch.command

        assertEquals(listOf("su", "-l", "dev"), command.takeLast(3))
        assertTrue("HOME=/home/dev" in command)
        assertEquals("/", command[command.indexOf("-w") + 1])
    }

    @Test
    fun `an initial command replaces the login shell as an argument list`() = runTest {
        val distro = install()

        val command = inDistro(
            planner.plan(ShellRequest(distro.id, listOf("ssh", "-p", "2222", "me@host")))
        ).launch.command

        assertEquals(listOf("ssh", "-p", "2222", "me@host"), command.takeLast(4))
        assertFalse("-l" in command.takeLast(5))
    }

    @Test
    fun `an initial command for another user is quoted once for su`() = runTest {
        val distro = install(user = "dev")

        val command = inDistro(
            planner.plan(ShellRequest(distro.id, listOf("ssh", "x'; reboot; '")))
        ).launch.command

        assertEquals(
            listOf("su", "-l", "dev", "-c", "'ssh' 'x'\\''; reboot; '\\'''"),
            command.takeLast(5)
        )
    }

    @Test
    fun `the resolv conf of the app is bound over the one of the distro`() = runTest {
        val distro = install()

        val command = inDistro(planner.plan(ShellRequest(distro.id))).launch.command

        assertTrue("/files/resolv.conf:/etc/resolv.conf" in command)
    }

    @Test
    fun `without a resolv conf the shell still starts, with a notice`() = runTest {
        val distro = install()
        dns.file = null

        val plan = inDistro(planner.plan(ShellRequest(distro.id)))

        assertEquals(LaunchNotice.DnsNotConfigured, plan.notice)
        assertFalse(plan.launch.command.any { it.endsWith(":/etc/resolv.conf") })
    }

    @Test
    fun `the shared storage is bound when the user turned it on`() = runTest {
        val distro = install()
        settings.update { it.copy(sharedStorage = true) }

        val plan = inDistro(planner.plan(ShellRequest(distro.id)))

        assertTrue("/storage/emulated/0:/root/storage/shared" in plan.launch.command)
        assertTrue(fileSystem.exists(path("distros/a1/rootfs/root/storage/shared")))
        assertNull(plan.notice)
    }

    @Test
    fun `the shared storage is left out, with a notice, when its permission is missing`() =
        runTest {
            val distro = install()
            settings.update { it.copy(sharedStorage = true) }
            access.granted = false

            val plan = inDistro(planner.plan(ShellRequest(distro.id)))

            assertEquals(
                LaunchNotice.StorageNotMounted(StorageDegradation.PERMISSION_DENIED),
                plan.notice
            )
            assertFalse(plan.launch.command.any { it.contains("/root/storage") })
        }

    @Test
    fun `a disabled shared storage touches nothing`() = runTest {
        val distro = install()

        planner.plan(ShellRequest(distro.id))

        assertEquals(0, access.permissionChecks)
        assertFalse(fileSystem.exists(path("distros/a1/rootfs/root/storage")))
    }

    @Test
    fun `compatibility mode runs proot without seccomp`() = runTest {
        val distro = install()
        settings.update { it.copy(prootCompatibilityMode = true) }

        val env = inDistro(planner.plan(ShellRequest(distro.id))).launch.environment

        assertEquals("1", env["PROOT_NO_SECCOMP"])
    }

    @Test
    fun `seccomp stays on by default`() = runTest {
        val distro = install()

        val env = inDistro(planner.plan(ShellRequest(distro.id))).launch.environment

        assertFalse("PROOT_NO_SECCOMP" in env)
    }

    @Test
    fun `a distro that is not ready falls back to the Android shell with a notice`() = runTest {
        val distro = install(state = DistroState.INSTALLING)

        val plan = planner.plan(ShellRequest(distro.id))

        assertEquals(LaunchPlan.FallbackToAndroid(LaunchNotice.DistroUnavailable("Alpine")), plan)
    }

    @Test
    fun `a distro that was removed falls back too, without a name`() = runTest {
        val plan = planner.plan(ShellRequest(distroId = 99L))

        assertEquals(LaunchPlan.FallbackToAndroid(LaunchNotice.DistroUnavailable(null)), plan)
    }

    @Test
    fun `a command never falls back to a shell that cannot run it`() = runTest {
        val distro = install(state = DistroState.FAILED)

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.DistroUnavailable("Alpine")),
            planner.plan(ShellRequest(distro.id, listOf("ssh", "host")))
        )
        assertEquals(
            LaunchPlan.Failed(LaunchProblem.DistroUnavailable(null)),
            planner.plan(ShellRequest(99L, listOf("ssh", "host")))
        )
        assertEquals(
            LaunchPlan.Failed(LaunchProblem.CommandNeedsDistro),
            planner.plan(ShellRequest(null, listOf("ssh", "host")))
        )
    }

    @Test
    fun `an empty command or one with a NUL is refused`() = runTest {
        val distro = install()

        listOf(emptyList(), listOf(""), listOf("ssh", "a\u0000b")).forEach { command ->
            assertEquals(
                LaunchPlan.Failed(LaunchProblem.InvalidCommand),
                planner.plan(ShellRequest(distro.id, command)),
                command.toString()
            )
        }
    }

    @Test
    fun `a ready distro whose files are gone is reported as damaged`() = runTest {
        val distro = install(withRootfs = false)

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.DistroCorrupt("Alpine")),
            planner.plan(ShellRequest(distro.id))
        )
    }

    @Test
    fun `missing proot binaries are reported, not crashed on`() = runTest {
        val distro = install()
        runtime.binaries = false

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.ProotMissing),
            planner.plan(ShellRequest(distro.id))
        )
    }

    @Test
    fun `no private temp directory is reported`() = runTest {
        val distro = install()
        runtime.tmpDir = null

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.TempDirUnavailable),
            planner.plan(ShellRequest(distro.id))
        )
    }

    @Test
    fun `a user name that could be an option is refused before anything runs`() = runTest {
        val distro = install()
        // The repository validates names; the planner still checks, as the database may be older.
        val risky = FakeUserRepository(distros, "-l")

        val plan = ProotSessionPlanner(
            risky,
            fileSystem,
            settings,
            SharedStorageMounts(fileSystem, access),
            runtime,
            dns
        ).plan(ShellRequest(distro.id))

        assertEquals(LaunchPlan.Failed(LaunchProblem.InvalidUser("-l")), plan)
        assertEquals(0, dns.calls)
    }

    @Test
    fun `a problem found early does not write the resolv conf or create directories`() = runTest {
        val distro = install(withRootfs = false)
        settings.update { it.copy(sharedStorage = true) }

        planner.plan(ShellRequest(distro.id))

        assertEquals(0, dns.calls)
        assertEquals(0, access.permissionChecks)
    }
}

/** A repository whose distros report [user] as their default user, whatever was stored. */
private class FakeUserRepository(
    private val inner: FakeDistroRepository,
    private val user: String
) : com.qtekfun.ultimateterminal.domain.repository.DistroRepository by inner {
    override suspend fun get(id: Long): Distro? = inner.get(id)?.copy(defaultUser = user)
}
