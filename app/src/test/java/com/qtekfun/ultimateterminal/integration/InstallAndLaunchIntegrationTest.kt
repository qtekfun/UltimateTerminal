// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.integration

import com.qtekfun.ultimateterminal.data.backup.Device
import com.qtekfun.ultimateterminal.data.proot.LaunchPlan
import com.qtekfun.ultimateterminal.data.proot.ProotRuntime
import com.qtekfun.ultimateterminal.data.proot.ProotSessionPlanner
import com.qtekfun.ultimateterminal.data.proot.ResolvConfSource
import com.qtekfun.ultimateterminal.domain.distro.InstallError
import com.qtekfun.ultimateterminal.domain.distro.InstallPhase
import com.qtekfun.ultimateterminal.domain.distro.InstallRequest
import com.qtekfun.ultimateterminal.domain.distro.InstallResult
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageMounts
import com.qtekfun.ultimateterminal.fakes.FakeSharedStorageAccess
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The flow of the app that no single test covers: a distro installed from an archive served over
 * HTTP (real downloader, real SHA-256 check, real transactional extraction into a real directory,
 * Room database) and then planned into a proot command line by the real planner. Only the edges
 * that need a device are fakes: the process itself, the shared storage and the native library.
 */
class InstallAndLaunchIntegrationTest {
    @TempDir
    lateinit var dir: File
    private lateinit var device: Device
    private val server = MockWebServer()
    private val archive = alpineTarGz()

    @BeforeEach
    fun setUp() {
        device = Device(File(dir, "device"))
        server.start()
    }

    @AfterEach
    fun tearDown() {
        device.close()
        runCatching { server.close() }
    }

    private fun serve(bytes: ByteArray = archive) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
    }

    private val url get() = server.url("/alpine.tar.gz").toString()

    private fun planner() = ProotSessionPlanner(
        device.distros,
        device.fileSystem,
        device.settings,
        SharedStorageMounts(device.fileSystem, FakeSharedStorageAccess(granted = false)),
        object : ProotRuntime {
            override val nativeLibraryDir = "/data/app/lib/arm64"

            override fun hasBinaries() = true

            override fun prepareTmpDir() = "/cache/proot-tmp"
        },
        object : ResolvConfSource {
            override suspend fun hostFile() = "/files/resolv.conf"
        }
    )

    @Test
    fun anInstalledDistroIsReadyAndItsSessionIsPlannedOnItsRootfs() = runBlocking {
        serve()
        val phases = mutableSetOf<InstallPhase>()

        val result = device.installerFor(url, archive).install(
            InstallRequest(DistroFamily.ALPINE, "Alpine")
        ) { phases += it.phase }

        val distro = (result as InstallResult.Success).distro
        assertEquals(DistroState.READY, distro.state)
        assertTrue(distro.isDefault)
        assertEquals("root", distro.defaultUser)
        assertTrue(
            phases.containsAll(
                listOf(
                    InstallPhase.RESOLVING,
                    InstallPhase.DOWNLOADING,
                    InstallPhase.EXTRACTING,
                    InstallPhase.FINALIZING
                )
            )
        )
        val tree = treeOf(device.distroDir(distro))
        assertEquals("-> /bin/busybox", tree["bin/sh"])
        assertEquals("file hello one\n", tree["root/notes.txt"])
        assertTrue(distro.sizeBytes > 0)
        // Nothing of the transaction is left: no staging tree, no downloaded archive.
        assertEquals(
            emptyList<String>(),
            File(device.storageRoot, "distros-tmp").list().orEmpty().toList()
        )
        assertEquals(distro, device.distros.getDefault())

        val plan = planner().plan(distro.id) as LaunchPlan.InDistro
        val command = plan.launch.command
        assertEquals("/data/app/lib/arm64/libproot.so", command.first())
        assertEquals(
            File(device.storageRoot, distro.directory.value).path,
            command[command.indexOf("-r") + 1]
        )
        assertTrue("-0" in command)
        assertEquals(listOf("/bin/sh", "-l"), command.takeLast(2))
        assertEquals(
            "/data/app/lib/arm64/libproot-loader.so",
            plan.launch.environment["PROOT_LOADER"]
        )
        assertTrue(command.contains("/files/resolv.conf:/etc/resolv.conf"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aNonRootDefaultUserIsCreatedInTheGuestAndTheSessionRunsAsIt() = runBlocking {
        serve()
        val distro = (
            device.installerFor(url, archive).install(
                InstallRequest(DistroFamily.ALPINE, "Alpine", "dev")
            ) as InstallResult.Success
            ).distro
        assertEquals("dev", distro.defaultUser)

        val plan = planner().plan(distro.id) as LaunchPlan.InDistro

        val command = plan.launch.command
        val passwd = File(device.distroDir(distro), "etc/passwd").readText()
        val line = passwd.lines().single { it.startsWith("dev:") }.split(":")
        val identity = command[command.indexOf("-i") + 1]
        assertEquals("${line[2]}:${line[3]}", identity)
        assertTrue("USER=dev" in command)
        assertTrue("-0" !in command)
        assertEquals(line[5], command[command.indexOf("-w") + 1])
        // The profile's user wins over the distro's default: root again.
        val asRoot = (
            planner().plan(
                distro.id,
                user = "root"
            ) as LaunchPlan.InDistro
            ).launch.command
        assertTrue("-0" in asRoot)
    }

    @Test
    fun anArchiveWhoseHashDoesNotMatchIsNeverInstalled() = runBlocking {
        serve(alpineTarGz("tampered"))

        val result = device.installerFor(url, archive).install(
            InstallRequest(DistroFamily.ALPINE, "Alpine")
        )

        val error = (result as InstallResult.Failure).error
        assertInstanceOf(InstallError.Download::class.java, error)
        val cause = (error as InstallError.Download).error
        assertTrue(cause is RootfsError.HashMismatch || cause is RootfsError.SizeMismatch)
        assertEquals(emptyList<Any>(), device.distros.observeAll().first())
        assertEquals(
            emptyList<String>(),
            File(device.storageRoot, "distros").list().orEmpty().toList()
        )
        assertFalse(
            Files.walk(device.storageRoot.toPath()).use { paths ->
                paths.anyMatch { it.fileName.toString().endsWith(".tar.gz") }
            }
        )
    }

    @Test
    fun aServerErrorLeavesNoDistroAndNoPlan() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())

        val result = device.installerFor(url, archive).install(
            InstallRequest(DistroFamily.ALPINE, "Alpine")
        )

        assertEquals(
            InstallError.Download(RootfsError.HttpStatus(404)),
            (result as InstallResult.Failure).error
        )
        assertEquals(emptyList<Any>(), device.distros.observeAll().first())
        assertNull(device.distros.getDefault())
        assertEquals(LaunchPlan.AndroidShell, planner().plan(null))
    }

    @Test
    fun aDistroWhoseFilesAreGoneIsReportedCorruptNotLaunched() = runBlocking {
        serve()
        val distro = (
            device.installerFor(url, archive).install(
                InstallRequest(DistroFamily.ALPINE, "Alpine")
            ) as InstallResult.Success
            ).distro
        device.distroDir(distro).deleteRecursively()

        val plan = planner().plan(distro.id)

        assertInstanceOf(LaunchPlan.Failed::class.java, plan)
    }
}
