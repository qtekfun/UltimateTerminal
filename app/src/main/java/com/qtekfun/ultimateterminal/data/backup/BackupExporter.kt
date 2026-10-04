// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.BackupPhase
import com.qtekfun.ultimateterminal.domain.backup.BackupProgress
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.backup.BackupSink
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.backup.ExportSummary
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Where a backup keeps its work files; everything under it is safe to delete when none runs. */
object BackupPaths {
    private const val ROOT = "backup-tmp"

    fun tempRoot(): FsPath = fixed(ROOT)

    fun tempDirectory(token: String): FsPath = fixed("$ROOT/$token")

    private fun fixed(raw: String): FsPath = requireNotNull(FsPath.of(raw).getOrNull()) {
        "not a safe path: $raw"
    }
}

/** What the exporter takes from its environment, so tests can fix the clock and the randomness. */
class ExportEnvironment(
    val clock: Clock = Clock.systemUTC(),
    val newToken: () -> String = { UUID.randomUUID().toString() },
    val random: (Int) -> ByteArray = secureRandom(),
    val kdfIterations: Int = BackupCrypto.DEFAULT_ITERATIONS
) {
    private companion object {
        fun secureRandom(): (Int) -> ByteArray {
            val generator = SecureRandom()
            return { size -> ByteArray(size).also(generator::nextBytes) }
        }
    }
}

/**
 * Writes backups (SPEC RF-06). Everything is prepared first, in a private work directory: the
 * configuration is read, each distro is archived and hashed. Only then the destination is opened,
 * so a failure while preparing leaves nothing behind, and one while writing discards the file.
 */
class BackupExporter(
    private val repositories: BackupRepositories,
    private val fileSystem: FileSystemRepository,
    private val appVersion: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val environment: ExportEnvironment = ExportEnvironment()
) {
    private val collector = ConfigCollector(repositories)
    private val archiver = DistroArchiver(fileSystem)

    suspend fun export(
        request: ExportRequest,
        sink: BackupSink,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult<ExportSummary> = withContext(io) {
        val work = BackupPaths.tempDirectory(environment.newToken())
        try {
            onProgress(BackupProgress(BackupPhase.PREPARING))
            when (val prepared = prepare(request, work, onProgress)) {
                is BackupResult.Failure -> prepared
                is BackupResult.Success -> write(prepared.value, request.password, sink, onProgress)
            }
        } finally {
            // It must also run when the export is cancelled, so it cannot be cancellable itself.
            withContext(NonCancellable) { fileSystem.deleteRecursively(work) }
        }
    }

    /** Removes work files an interrupted export left; call it only when no export is running. */
    suspend fun recoverInterrupted() {
        fileSystem.deleteRecursively(BackupPaths.tempRoot())
    }

    private class Prepared(
        val kind: BackupKind,
        val manifest: ByteArray,
        val config: ByteArray?,
        val distros: List<ArchivedDistro>,
        val sshKeys: Int
    )

    private suspend fun prepare(
        request: ExportRequest,
        work: FsPath,
        onProgress: (BackupProgress) -> Unit
    ): BackupResult<Prepared> {
        val selected = select(request)
        val collected: BackupResult<ConfigSnapshot?> = when (request.kind) {
            BackupKind.DISTRO -> BackupResult.Success(null)
            else -> collector.collect(request.includeSshKeys)
        }
        if (request.kind == BackupKind.DISTRO && selected.isEmpty()) {
            return BackupResult.Failure(BackupError.DistroUnavailable)
        }
        return when (collected) {
            is BackupResult.Failure -> collected

            is BackupResult.Success -> {
                val problem = preflight(request, selected, collected.value, work)
                if (problem == null) {
                    archive(request, selected, collected.value, work, onProgress)
                } else {
                    BackupResult.Failure(problem)
                }
            }
        }
    }

    private suspend fun select(request: ExportRequest): List<Distro> {
        val ready = repositories.distros.observeAll().first().filter {
            it.state == DistroState.READY
        }
        // Only a distro backup names one; for a configuration backup nothing can match.
        val wanted = if (request.kind == BackupKind.DISTRO) request.distroId else null
        return if (request.kind == BackupKind.ALL) ready else ready.filter { it.id == wanted }
    }

    /** What would make the export fail before any work is done, or null. */
    private suspend fun preflight(
        request: ExportRequest,
        selected: List<Distro>,
        snapshot: ConfigSnapshot?,
        work: FsPath
    ): BackupError? {
        val required = selected.sumOf {
            (fileSystem.sizeOf(it.directory) as? Outcome.Success)?.value ?: 0L
        }
        val available = fileSystem.freeSpaceBytes()
        val created = fileSystem.createDirectories(work)
        return when {
            snapshot.keyCount() > 0 && request.password.isNullOrEmpty() ->
                BackupError.KeysNeedPassword

            available < required -> BackupError.InsufficientSpace(required, available)

            created is Outcome.Failure -> BackupError.Io(created.error.toString())

            else -> null
        }
    }

    private suspend fun archive(
        request: ExportRequest,
        selected: List<Distro>,
        snapshot: ConfigSnapshot?,
        work: FsPath,
        onProgress: (BackupProgress) -> Unit
    ): BackupResult<Prepared> {
        val archived = ArrayList<ArchivedDistro>()
        try {
            for ((index, distro) in selected.withIndex()) {
                archived.add(archiver.archive(distro, work, index, selected.size, onProgress))
            }
        } catch (e: IOException) {
            return BackupResult.Failure(e.toBackupError())
        }
        val config = snapshot?.let(ConfigCodec::encode)
        val manifest = BackupManifest(
            ManifestCodec.FORMAT,
            appVersion,
            request.kind.name,
            Instant.now(environment.clock).toString(),
            listOfNotNull(config?.let(::configPart)) + archived.map { it.part }
        )
        return BackupResult.Success(
            Prepared(
                request.kind,
                ManifestCodec.encode(manifest),
                config,
                archived,
                snapshot.keyCount()
            )
        )
    }

    private suspend fun write(
        prepared: Prepared,
        rawPassword: String?,
        sink: BackupSink,
        onProgress: (BackupProgress) -> Unit
    ): BackupResult<ExportSummary> {
        val password = rawPassword?.takeIf { it.isNotEmpty() }
        var opened = false
        return try {
            val counting = CountingOutputStream(sink.open().also { opened = true })
            val stream = if (password == null) counting else encrypting(counting, password)
            ContainerWriter(stream).use { container -> copyParts(container, prepared, onProgress) }
            val summary = ExportSummary(
                prepared.kind,
                prepared.distros.size,
                prepared.sshKeys,
                password != null,
                counting.bytes
            )
            BackupResult.Success(summary)
        } catch (e: IOException) {
            if (opened) withContext(NonCancellable) { sink.discard() }
            BackupResult.Failure(e.toBackupError())
        }
    }

    private fun copyParts(
        container: ContainerWriter,
        prepared: Prepared,
        onProgress: (BackupProgress) -> Unit
    ) {
        container.bytes(PartNames.MANIFEST, prepared.manifest)
        prepared.config?.let { container.bytes(PartNames.CONFIG, it) }
        for ((index, archived) in prepared.distros.withIndex()) {
            onProgress(BackupProgress(BackupPhase.WRITING, index.toFloat() / prepared.distros.size))
            Files.newInputStream(archived.file).use {
                container.stream(archived.part.name, archived.part.size, it)
            }
        }
    }

    private fun encrypting(out: OutputStream, password: String): OutputStream {
        val iterations = environment.kdfIterations
        val header = EncryptionHeader(
            iterations,
            environment.random(BackupCrypto.SALT_BYTES),
            environment.random(BackupCrypto.NONCE_PREFIX_BYTES),
            BackupCrypto.DEFAULT_CHUNK_BYTES
        )
        return EncryptingOutputStream(
            out,
            BackupCrypto.deriveKey(password, header.salt, iterations),
            header
        )
    }
}

/** Counts what goes through, to tell the user how big the file is. */
internal class CountingOutputStream(private val out: OutputStream) : OutputStream() {
    var bytes: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        bytes++
    }

    override fun write(data: ByteArray, off: Int, len: Int) {
        out.write(data, off, len)
        bytes += len
    }

    override fun flush() = out.flush()

    override fun close() = out.close()
}

/** How many private keys a snapshot carries; a distro backup has no snapshot at all. */
private fun ConfigSnapshot?.keyCount(): Int = if (this == null) 0 else sshKeys.size

private fun configPart(bytes: ByteArray): ManifestPart {
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
    return ManifestPart(
        PartNames.CONFIG,
        hash.joinToString("") {
            "%02x".format(it)
        },
        bytes.size.toLong()
    )
}
