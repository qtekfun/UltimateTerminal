// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.data.local.UltimateTerminalDatabase
import com.qtekfun.ultimateterminal.data.local.inMemoryDatabase
import com.qtekfun.ultimateterminal.data.repository.RoomDistroRepository
import com.qtekfun.ultimateterminal.data.repository.RoomLayoutRepository
import com.qtekfun.ultimateterminal.data.repository.RoomProfileRepository
import com.qtekfun.ultimateterminal.data.repository.RoomSettingsRepository
import com.qtekfun.ultimateterminal.data.repository.RoomSshHostRepository
import com.qtekfun.ultimateterminal.data.rootfs.TarGzExtractor
import com.qtekfun.ultimateterminal.data.storage.NioFileSystemRepository
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.backup.BackupSink
import com.qtekfun.ultimateterminal.domain.backup.BackupSource
import com.qtekfun.ultimateterminal.domain.distro.DistroPaths
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.fakes.FakeSshKeyStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers

/** Free space as the host JVM reports it; on a device the app asks the file system instead. */
private val JVM_FREE_SPACE: (Path) -> Long = { it.toFile().usableSpace }

/** The backup file is plain ASCII where it matters, so tests can find and change its bytes. */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Success value of a result, failing the test with the error otherwise. */
fun <T> BackupResult<T>.value(): T = when (this) {
    is BackupResult.Success -> value
    is BackupResult.Failure -> throw AssertionError("expected success, got $error")
}

fun BackupResult<*>.errorOrNull() = (this as? BackupResult.Failure)?.error

/**
 * One device: a storage directory with the real file system repository, and the Room-backed
 * repositories on an in-memory database. Two devices stand for an old phone and a new one.
 */
class Device(root: File) : AutoCloseable {
    val storageRoot = File(root, "storage").also { it.mkdirs() }
    val fileSystem = NioFileSystemRepository(storageRoot.toPath(), Dispatchers.IO, JVM_FREE_SPACE)
    private val db: UltimateTerminalDatabase = inMemoryDatabase()
    val settings = RoomSettingsRepository(db.settingDao())
    val profiles = RoomProfileRepository(db.profileDao())
    val layouts = RoomLayoutRepository(db.layoutDao())
    val hosts = RoomSshHostRepository(db.sshHostDao())
    val distros = RoomDistroRepository(db.distroDao(), Clock.systemUTC())
    val keys = FakeSshKeyStore()
    val extractor = TarGzExtractor(fileSystem, Dispatchers.IO)
    private var tokens = 0
    private var distroCounter = 0

    val repositories get() = BackupRepositories(settings, profiles, layouts, hosts, distros, keys)

    fun environment(iterations: Int = FAST_ITERATIONS) = ExportEnvironment(
        clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC),
        newToken = { "w${tokens++}" },
        random = { size -> ByteArray(size) { (it + 1).toByte() } },
        kdfIterations = iterations
    )

    fun exporter(
        repos: BackupRepositories = repositories,
        files: com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository = fileSystem,
        iterations: Int = FAST_ITERATIONS
    ) = BackupExporter(repos, files, "0.1.0-test", Dispatchers.IO, environment(iterations))

    fun restorer(
        repos: BackupRepositories = repositories,
        files: com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository = fileSystem
    ) = BackupRestorer(repos, files, extractor, Dispatchers.IO) { "r${tokens++}" }

    /** Registers a ready distro whose files [fill] puts in its directory. */
    suspend fun addDistro(
        name: String,
        fill: (File) -> Unit = { it.resolve("etc").mkdirs() }
    ): Distro {
        val directory = DistroPaths.distroDirectory("seed${distroCounter++}")
        val added = distros.add(
            NewDistro(name, DistroType.ALPINE, "3.20", directory, defaultUser = "root")
        ) as Outcome.Success
        val dir = File(storageRoot, directory.value).also { it.mkdirs() }
        fill(dir)
        val size = (fileSystem.sizeOf(directory) as Outcome.Success).value
        distros.updateState(added.value.id, DistroState.READY, size)
        return distros.get(added.value.id)!!
    }

    fun distroDir(distro: Distro) = File(storageRoot, distro.directory.value)

    override fun close() = db.close()

    companion object {
        /** The least the format allows: the tests do not need a slow key derivation. */
        const val FAST_ITERATIONS = BackupCrypto.MIN_ITERATIONS
    }
}

/** A source that hands out a chosen byte array on each open (the last one repeats). */
class BytesSource(private vararg val versions: ByteArray) : BackupSource {
    var opens = 0
        private set

    override suspend fun open(): InputStream =
        ByteArrayInputStream(versions[minOf(opens++, versions.size - 1)])
}

class MemorySink : BackupSink {
    val buffer = ByteArrayOutputStream()
    var opened = false
    var discarded = false

    override suspend fun open(): OutputStream {
        opened = true
        return buffer
    }

    override suspend fun discard() {
        discarded = true
    }

    fun bytes(): ByteArray = buffer.toByteArray()
}

/** A destination that cannot be opened, or that fails after [limit] bytes. */
class FailingSink(private val limit: Int? = null) : BackupSink {
    var opened = false
    var discarded = false

    override suspend fun open(): OutputStream {
        opened = limit != null
        return when (limit) {
            null -> throw java.io.IOException("cannot open the destination")
            else -> LimitedOutputStream(limit)
        }
    }

    override suspend fun discard() {
        discarded = true
    }
}

private class LimitedOutputStream(private var left: Int) : OutputStream() {
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(data: ByteArray, off: Int, len: Int) {
        left -= len
        if (left < 0) throw java.io.IOException("No space left on device")
    }
}

/** A file system that can be told to run out of space or to refuse some operations. */
class FlakyFileSystem(
    private val inner: com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository,
    private val freeSpace: Long? = null,
    private val failCreate: Boolean = false,
    private val failMove: Boolean = false
) : com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository by inner {
    override suspend fun freeSpaceBytes(): Long = freeSpace ?: inner.freeSpaceBytes()

    override suspend fun createDirectories(path: com.qtekfun.ultimateterminal.domain.model.FsPath) =
        if (failCreate) {
            Outcome.Failure(com.qtekfun.ultimateterminal.domain.DomainError.Io("read-only"))
        } else {
            inner.createDirectories(path)
        }

    override suspend fun move(
        from: com.qtekfun.ultimateterminal.domain.model.FsPath,
        to: com.qtekfun.ultimateterminal.domain.model.FsPath
    ) = if (failMove) {
        Outcome.Failure(com.qtekfun.ultimateterminal.domain.DomainError.Io("cannot rename"))
    } else {
        inner.move(from, to)
    }
}

/** A distro repository whose registrations or state updates can be made to fail. */
class FlakyDistros(
    private val inner: com.qtekfun.ultimateterminal.domain.repository.DistroRepository,
    private val addError: com.qtekfun.ultimateterminal.domain.DomainError? = null,
    private val failUpdate: Boolean = false
) : com.qtekfun.ultimateterminal.domain.repository.DistroRepository by inner {
    override suspend fun add(distro: NewDistro): Outcome<Distro> =
        addError?.let { Outcome.Failure(it) } ?: inner.add(distro)

    override suspend fun updateState(id: Long, state: DistroState, sizeBytes: Long?) =
        if (failUpdate) {
            Outcome.Failure(com.qtekfun.ultimateterminal.domain.DomainError.Io("disk error"))
        } else {
            inner.updateState(id, state, sizeBytes)
        }
}

/** A key store that lists keys it cannot read. */
class UnreadableKeys(private val inner: com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore) :
    com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore by inner {
    override suspend fun privateKey(alias: String) =
        com.qtekfun.ultimateterminal.domain.ssh.SshResult.Failure(
            com.qtekfun.ultimateterminal.domain.ssh.SshError.KeyNotFound
        )
}

suspend fun Device.exportBytes(
    request: com.qtekfun.ultimateterminal.domain.backup.ExportRequest
): ByteArray {
    val sink = MemorySink()
    exporter().export(request, sink).value()
    return sink.bytes()
}
