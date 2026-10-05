// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.launch.LaunchNotice
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem
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

private class FakeProcFiles(var files: Map<String, String> = emptyMap()) : FakeProcSource {
    override suspend fun hostFiles(): Map<String, String> = files
}

class ProotSessionPlannerTest {
    private val distros = FakeDistroRepository()
    private val fileSystem = InMemoryFileSystemRepository()
    private val settings = FakeSettingsRepository()
    private val access = FakeSharedStorageAccess()
    private val runtime = FakeRuntime()
    private val dns = FakeDns()
    private val fakeProc = FakeProcFiles()
    private val planner = ProotSessionPlanner(
        distros,
        fileSystem,
        settings,
        SharedStorageMounts(fileSystem, access),
        runtime,
        dns,
        fakeProc
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
        if (withRootfs) fileSystem.createDirectories(path("distros/a1"))
        return checkNotNull(distros.get(added.value.id))
    }

    private fun inDistro(plan: LaunchPlan) = plan as LaunchPlan.InDistro

    @Test
    fun `a tab with no distro and no command opens the Android shell`() = runTest {
        assertEquals(LaunchPlan.AndroidShell, planner.plan(null))
    }

    @Test
    fun `a ready distro starts proot on its root filesystem`() = runTest {
        val distro = install()

        val plan = inDistro(planner.plan(distro.id))

        val command = plan.launch.command
        assertEquals("/data/app/lib/arm64/libproot.so", command.first())
        assertEquals("/storage/distros/a1", command[command.indexOf("-r") + 1])
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

        val command = inDistro(planner.plan(distro.id)).launch.command

        assertEquals("/root", command[command.indexOf("-w") + 1])
        assertTrue("-0" in command)
        assertTrue("HOME=/root" in command)
    }

    private fun putAccounts(passwd: String, group: String = "root:x:0:\n") {
        fileSystem.putFile("distros/a1/etc/passwd", passwd.toByteArray())
        fileSystem.putFile("distros/a1/etc/group", group.toByteArray())
    }

    @Test
    fun `an existing user runs its shell with the ids of passwd and no su`() = runTest {
        putAccounts("root:x:0:0:r:/root:/bin/sh\ndev:x:1500:1600::/home/dev:/bin/bash\n")
        val distro = install(user = "dev")

        val command = inDistro(planner.plan(distro.id)).launch.command

        assertEquals("1500:1600", command[command.indexOf("-i") + 1])
        assertEquals(listOf("/bin/bash", "-l"), command.takeLast(2))
        assertTrue("HOME=/home/dev" in command)
        assertFalse("su" in command)
        assertEquals("/home/dev", command[command.indexOf("-w") + 1])
    }

    @Test
    fun `a missing user is created with a free id and a home`() = runTest {
        putAccounts("root:x:0:0:r:/root:/bin/sh\n")
        val distro = install(user = "dev")

        val command = inDistro(planner.plan(distro.id)).launch.command

        assertEquals("1000:1000", command[command.indexOf("-i") + 1])
        assertEquals(listOf("/bin/sh", "-l"), command.takeLast(2))
        assertTrue(
            checkNotNull(fileSystem.readFile("distros/a1/etc/passwd")).decodeToString()
                .contains("dev:x:1000:1000:dev:/home/dev:/bin/sh\n")
        )
        assertTrue(fileSystem.exists(path("distros/a1/home/dev")))
        assertEquals("/home/dev", command[command.indexOf("-w") + 1])
    }

    @Test
    fun `a created user gets bash when etc shells lists it`() = runTest {
        putAccounts("root:x:0:0:r:/root:/bin/sh")
        fileSystem.putFile("distros/a1/etc/shells", "/bin/sh\n/bin/bash\n".toByteArray())
        val distro = install(user = "dev")

        val command = inDistro(planner.plan(distro.id)).launch.command

        assertEquals(listOf("/bin/bash", "-l"), command.takeLast(2))
        assertTrue(
            checkNotNull(fileSystem.readFile("distros/a1/etc/passwd")).decodeToString()
                .startsWith("root:x:0:0:r:/root:/bin/sh\ndev:")
        )
    }

    @Test
    fun `a user that cannot be found or created stops the launch with a clear problem`() = runTest {
        val distro = install(user = "dev")

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.UserUnavailable("dev")),
            planner.plan(distro.id)
        )
    }

    @Test
    fun `the resolv conf of the app is bound over the one of the distro`() = runTest {
        val distro = install()

        val command = inDistro(planner.plan(distro.id)).launch.command

        assertTrue("/files/resolv.conf:/etc/resolv.conf" in command)
    }

    @Test
    fun `the fake proc files are bound over the real proc, after it`() = runTest {
        val distro = install()
        fakeProc.files =
            mapOf("uptime" to "/files/fake-proc/uptime", "stat" to "/files/fake-proc/stat")

        val command = inDistro(planner.plan(distro.id)).launch.command

        // The binds are applied in order: a file over /proc only works if /proc came first.
        val proc = command.indexOf("/proc:/proc")
        val stat = command.indexOf("/files/fake-proc/stat:/proc/stat")
        val uptime = command.indexOf("/files/fake-proc/uptime:/proc/uptime")
        assertTrue(proc >= 0)
        assertTrue(stat > proc)
        assertTrue(uptime > stat)
        assertEquals("-b", command[stat - 1])
    }

    @Test
    fun `without fake proc files the real proc is all there is and the shell starts`() = runTest {
        val distro = install()
        fakeProc.files = emptyMap()

        val command = inDistro(planner.plan(distro.id)).launch.command

        assertFalse(command.any { it.contains(":/proc/") })
    }

    @Test
    fun `without a resolv conf the shell still starts, with a notice`() = runTest {
        val distro = install()
        dns.file = null

        val plan = inDistro(planner.plan(distro.id))

        assertEquals(LaunchNotice.DnsNotConfigured, plan.notice)
        assertFalse(plan.launch.command.any { it.endsWith(":/etc/resolv.conf") })
    }

    @Test
    fun `the shared storage is bound when the user turned it on`() = runTest {
        val distro = install()
        settings.update { it.copy(sharedStorage = true) }

        val plan = inDistro(planner.plan(distro.id))

        assertTrue("/storage/emulated/0:/root/storage/shared" in plan.launch.command)
        assertTrue(fileSystem.exists(path("distros/a1/root/storage/shared")))
        assertNull(plan.notice)
    }

    @Test
    fun `the shared storage is left out, with a notice, when its permission is missing`() =
        runTest {
            val distro = install()
            settings.update { it.copy(sharedStorage = true) }
            access.granted = false

            val plan = inDistro(planner.plan(distro.id))

            assertEquals(
                LaunchNotice.StorageNotMounted(StorageDegradation.PERMISSION_DENIED),
                plan.notice
            )
            assertFalse(plan.launch.command.any { it.contains("/root/storage") })
        }

    @Test
    fun `a disabled shared storage touches nothing`() = runTest {
        val distro = install()

        planner.plan(distro.id)

        assertEquals(0, access.permissionChecks)
        assertFalse(fileSystem.exists(path("distros/a1/root/storage")))
    }

    @Test
    fun `compatibility mode runs proot without seccomp`() = runTest {
        val distro = install()
        settings.update { it.copy(prootCompatibilityMode = true) }

        val env = inDistro(planner.plan(distro.id)).launch.environment

        assertEquals("1", env["PROOT_NO_SECCOMP"])
    }

    @Test
    fun `seccomp stays on by default`() = runTest {
        val distro = install()

        val env = inDistro(planner.plan(distro.id)).launch.environment

        assertFalse("PROOT_NO_SECCOMP" in env)
    }

    @Test
    fun `a distro that is not ready falls back to the Android shell with a notice`() = runTest {
        val distro = install(state = DistroState.INSTALLING)

        val plan = planner.plan(distro.id)

        assertEquals(LaunchPlan.FallbackToAndroid(LaunchNotice.DistroUnavailable("Alpine")), plan)
    }

    @Test
    fun `a distro that was removed falls back too, without a name`() = runTest {
        val plan = planner.plan(99L)

        assertEquals(LaunchPlan.FallbackToAndroid(LaunchNotice.DistroUnavailable(null)), plan)
    }

    @Test
    fun `proot is rooted at the distro directory, not at a rootfs folder inside it`() = runTest {
        val distro = install()

        val command = inDistro(planner.plan(distro.id)).launch.command

        assertEquals("/storage/distros/a1", command[command.indexOf("-r") + 1])
        assertFalse(command.any { it.endsWith("/rootfs") })
    }

    @Test
    fun `a ready distro whose files are gone is reported as damaged`() = runTest {
        val distro = install(withRootfs = false)

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.DistroCorrupt("Alpine")),
            planner.plan(distro.id)
        )
    }

    @Test
    fun `missing proot binaries are reported, not crashed on`() = runTest {
        val distro = install()
        runtime.binaries = false

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.ProotMissing),
            planner.plan(distro.id)
        )
    }

    @Test
    fun `no private temp directory is reported`() = runTest {
        val distro = install()
        runtime.tmpDir = null

        assertEquals(
            LaunchPlan.Failed(LaunchProblem.TempDirUnavailable),
            planner.plan(distro.id)
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
        ).plan(distro.id)

        assertEquals(LaunchPlan.Failed(LaunchProblem.InvalidUser("-l")), plan)
        assertEquals(0, dns.calls)
    }

    @Test
    fun `a problem found early does not write the resolv conf or create directories`() = runTest {
        val distro = install(withRootfs = false)
        settings.update { it.copy(sharedStorage = true) }

        planner.plan(distro.id)

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
