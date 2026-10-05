// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.support

import android.content.Context
import android.os.Build
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimateterminal.data.backup.BackupExporter
import com.qtekfun.ultimateterminal.data.backup.BackupRepositories
import com.qtekfun.ultimateterminal.data.backup.BackupRestorer
import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.proot.LaunchPlan
import com.qtekfun.ultimateterminal.data.proot.ProotSessionPlanner
import com.qtekfun.ultimateterminal.data.proot.ResolvConfSource
import com.qtekfun.ultimateterminal.data.repository.RoomDistroRepository
import com.qtekfun.ultimateterminal.data.repository.RoomLayoutRepository
import com.qtekfun.ultimateterminal.data.repository.RoomProfileRepository
import com.qtekfun.ultimateterminal.data.repository.RoomSettingsRepository
import com.qtekfun.ultimateterminal.data.repository.RoomSshHostRepository
import com.qtekfun.ultimateterminal.data.rootfs.HttpRootfsDownloader
import com.qtekfun.ultimateterminal.data.rootfs.OfficialRootfsCatalog
import com.qtekfun.ultimateterminal.data.rootfs.TarGzExtractor
import com.qtekfun.ultimateterminal.data.storage.FileTrees
import com.qtekfun.ultimateterminal.data.storage.NioFileSystemRepository
import com.qtekfun.ultimateterminal.domain.distro.DistroInstaller
import com.qtekfun.ultimateterminal.domain.distro.InstallError
import com.qtekfun.ultimateterminal.domain.distro.InstallRequest
import com.qtekfun.ultimateterminal.domain.distro.InstallResult
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageAccess
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageMounts
import com.qtekfun.ultimateterminal.platform.AndroidFreeSpace
import com.qtekfun.ultimateterminal.platform.AndroidProotRuntime
import java.io.File
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

/** No SSH keys: these tests do not touch them, and the app's key store needs the Keystore. */
private object NoKeys : SshKeyStore {
    override fun observe(): Flow<List<SshKeyInfo>> = flowOf(emptyList())

    override suspend fun put(info: SshKeyInfo, privateKey: String): SshResult<Unit> =
        error("these tests have no keys")

    override suspend fun privateKey(alias: String): SshResult<String> =
        error("these tests have no keys")

    override suspend fun delete(alias: String): SshResult<Unit> = error("these tests have no keys")

    override fun newAlias(): String = error("these tests have no keys")
}

/** A device whose shared storage is never available: the tests never mount it. */
private object NoSharedStorage : SharedStorageAccess {
    override fun isPermissionGranted() = false

    override fun sharedRoot(): String? = null

    override fun directoryExists(absolutePath: String) = false
}

/**
 * The app's own classes over a private world of their own: a directory under the app's files
 * (`it-t18-<random>`, deleted by [close]) and an in-memory database. It never opens the app's
 * real database nor its `files/storage`, so the distros the user installed are out of reach of
 * every test that uses it.
 */
internal class TestStorage(private val context: Context) : AutoCloseable {
    private val base = File(context.filesDir, "it-t18-${UUID.randomUUID()}").also { it.mkdirs() }
    val storageRoot = File(base, "storage").also { it.mkdirs() }
    val fileSystem =
        NioFileSystemRepository(storageRoot.toPath(), Dispatchers.IO, AndroidFreeSpace)
    private val db: UltimateTerminalDatabase =
        Room.inMemoryDatabaseBuilder<UltimateTerminalDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
    val settings = RoomSettingsRepository(db.settingDao())
    val profiles = RoomProfileRepository(db.profileDao())
    val layouts = RoomLayoutRepository(db.layoutDao())
    val hosts = RoomSshHostRepository(db.sshHostDao())
    val distros = RoomDistroRepository(db.distroDao(), Clock.systemUTC())
    private val extractor = TarGzExtractor(fileSystem, Dispatchers.IO)
    private val repositories =
        BackupRepositories(settings, profiles, layouts, hosts, distros, NoKeys)
    val runtime = AndroidProotRuntime(context)

    val exporter = BackupExporter(repositories, fileSystem, "it-test", Dispatchers.IO)
    val restorer = BackupRestorer(repositories, fileSystem, extractor, Dispatchers.IO)

    val planner = ProotSessionPlanner(
        distros,
        fileSystem,
        settings,
        SharedStorageMounts(fileSystem, NoSharedStorage),
        runtime,
        // No resolv.conf bind: the guest does not need the network for these tests.
        ResolvConfSource { null }
    )

    /** A file in this world's scratch directory (for backups), outside the storage root. */
    fun scratch(name: String): File = File(base, name)

    /**
     * Installs Alpine with the app's own installer: the official index gives the URL and the
     * SHA-256, the downloader checks it, and only a verified archive is unpacked. A test that has
     * no network is skipped (an Assumption), never failed; a wrong hash or a server error is a
     * failure, because that is exactly what the verification is for.
     */
    fun installAlpine(name: String = "Alpine"): Distro = runBlocking {
        val client = OkHttpClient()
        val installer = DistroInstaller(
            catalog = OfficialRootfsCatalog(client),
            downloader = HttpRootfsDownloader(client),
            extractor = extractor,
            fileSystem = fileSystem,
            distros = distros,
            architecture = { Architecture.fromAbis(Build.SUPPORTED_ABIS.toList()) },
            supportedAbis = { Build.SUPPORTED_ABIS.toList() }
        )
        val result = withTimeout(INSTALL_TIMEOUT_MS) {
            installer.install(InstallRequest(DistroFamily.ALPINE, name))
        }
        assumeTrue(
            "no network to reach the official Alpine index: " +
                "${(result as? InstallResult.Failure)?.error}",
            !result.isOffline()
        )
        when (result) {
            is InstallResult.Success -> result.distro
            is InstallResult.Failure -> throw AssertionError("install failed: ${result.error}")
        }
    }

    /** The planned launch of [distro], failing the test if the app cannot start it. */
    fun launchOf(distro: Distro, user: String? = null): LaunchPlan.InDistro = runBlocking {
        assertTrue("proot is not in the native library directory", runtime.hasBinaries())
        val plan = planner.plan(distro.id, user)
        plan as? LaunchPlan.InDistro ?: throw AssertionError("not launchable: $plan")
    }

    override fun close() {
        db.close()
        runCatching { FileTrees.delete(base.toPath()) }
    }

    private fun InstallResult.isOffline(): Boolean {
        val error = (this as? InstallResult.Failure)?.error
        val catalogDown = error is InstallError.Catalog &&
            error.error is RootfsError.CatalogUnavailable
        val downloadDown = error is InstallError.Download && error.error is RootfsError.Network
        return catalogDown || downloadDown
    }

    private companion object {
        const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
