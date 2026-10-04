// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupPhase
import com.qtekfun.ultimateterminal.domain.backup.BackupProbe
import com.qtekfun.ultimateterminal.domain.backup.BackupProgress
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.backup.BackupSource
import com.qtekfun.ultimateterminal.domain.backup.RestoreSummary
import com.qtekfun.ultimateterminal.domain.backup.StreamRootfsExtractor
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Reads backups (SPEC RF-06). A restore makes two passes over the file. The first checks all of
 * it (the encryption's tags, the manifest, the hash and size of every part, the configuration) and
 * writes nothing; only if it is clean does the second one restore. A distro is all-or-nothing
 * (see [DistroRestorer]); the configuration is applied last, after the distros it may refer to.
 */
class BackupRestorer(
    repositories: BackupRepositories,
    fileSystem: FileSystemRepository,
    extractor: StreamRootfsExtractor,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    newToken: () -> String = { UUID.randomUUID().toString() }
) {
    private val applier = ConfigApplier(repositories)
    private val distroRestorer =
        DistroRestorer(repositories.distros, fileSystem, extractor, newToken)

    /** Whether [source] is encrypted, so the caller knows to ask for a password. */
    suspend fun probe(source: BackupSource): BackupResult<BackupProbe> = withContext(io) {
        guard {
            BufferedInputStream(source.open()).use { input ->
                BackupProbe(BackupOpener.isEncrypted(input))
            }
        }
    }

    suspend fun restore(
        source: BackupSource,
        password: String? = null,
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupResult<RestoreSummary> = withContext(io) {
        guard {
            onProgress(BackupProgress(BackupPhase.VERIFYING))
            val manifest = verify(source, password)
            apply(source, password, manifest, onProgress)
        }
    }

    private suspend fun <T> guard(block: suspend () -> T): BackupResult<T> = try {
        BackupResult.Success(block())
    } catch (e: IOException) {
        BackupResult.Failure(e.toBackupError())
    }

    /** First pass: reads everything and checks it. Nothing is written. */
    private suspend fun verify(source: BackupSource, password: String?): BackupManifest =
        ContainerReader(BackupOpener.open(source, password)).use { reader ->
            val manifest = readManifest(reader)
            val seen = HashSet<String>()
            val context = currentCoroutineContext()
            var entry = reader.next()
            while (entry != null) {
                context.ensureActive()
                val part = partFor(manifest, entry.name)
                if (!seen.add(part.name)) {
                    throw BackupFailure(BackupError.InvalidManifest("a part is repeated"))
                }
                checkPart(part, entry.content, keepConfig = false)
                entry = reader.next()
            }
            if (seen.size != manifest.parts.size) throw BackupFailure(BackupError.Truncated)
            manifest
        }

    /** Second pass: restores, checking every part again as it goes. */
    private suspend fun apply(
        source: BackupSource,
        password: String?,
        manifest: BackupManifest,
        onProgress: (BackupProgress) -> Unit
    ): RestoreSummary {
        val restored = LinkedHashMap<String, Long>()
        var config: ConfigSnapshot? = null
        ContainerReader(BackupOpener.open(source, password)).use { reader ->
            readManifest(reader)
            var entry = reader.next()
            while (entry != null) {
                val part = partFor(manifest, entry.name)
                if (part.name == PartNames.CONFIG) {
                    config = checkPart(part, entry.content, keepConfig = true)
                } else {
                    onProgress(BackupProgress(BackupPhase.RESTORING, null))
                    val distro = distroRestorer.restore(part, entry.content) {
                        onProgress(BackupProgress(BackupPhase.RESTORING, it))
                    }
                    restored[part.distro!!.name] = distro.id
                }
                entry = reader.next()
            }
        }
        onProgress(BackupProgress(BackupPhase.APPLYING))
        val applied = config?.let { applier.apply(it, restored) }
        return RestoreSummary(
            distros = restored.size,
            settingsApplied = applied != null,
            profiles = applied?.profiles ?: 0,
            layouts = applied?.layouts ?: 0,
            sshHosts = applied?.sshHosts ?: 0,
            sshKeys = applied?.sshKeys ?: 0,
            skipped = applied?.skipped ?: 0
        )
    }

    /** The manifest's description of the part [name]; the file may have changed between passes. */
    private fun partFor(manifest: BackupManifest, name: String): ManifestPart =
        manifest.parts.firstOrNull { it.name == name }
            ?: throw BackupFailure(BackupError.InvalidManifest("a part is not in the manifest"))

    /** The first entry must be the manifest, and a small one. */
    private fun readManifest(reader: ContainerReader): BackupManifest {
        val first = reader.next()?.takeIf { it.name == PartNames.MANIFEST }
            ?: throw BackupFailure(BackupError.NotABackup)
        val bytes = readBounded(first.content, ManifestCodec.MAX_BYTES, "the manifest")
        return when (val decoded = ManifestCodec.decode(bytes)) {
            is BackupResult.Failure -> throw BackupFailure(decoded.error)
            is BackupResult.Success -> decoded.value
        }
    }

    /**
     * Checks one part against the manifest. A configuration is also parsed (and returned when
     * [keepConfig]); a distro is only read through, so it is hashed without being kept.
     */
    private fun checkPart(
        part: ManifestPart,
        content: InputStream,
        keepConfig: Boolean
    ): ConfigSnapshot? {
        val digest = DigestingInputStream(content)
        val snapshot = if (part.name == PartNames.CONFIG) {
            val bytes = readBounded(digest, MAX_CONFIG_BYTES, "the configuration")
            when (val decoded = ConfigCodec.decode(bytes)) {
                is BackupResult.Failure -> throw BackupFailure(decoded.error)
                is BackupResult.Success -> decoded.value
            }
        } else {
            null
        }
        digest.drain()
        if (digest.bytes != part.size || digest.sha256() != part.sha256) {
            throw BackupFailure(BackupError.HashMismatch(part.name))
        }
        return snapshot.takeIf { keepConfig }
    }

    private fun readBounded(input: InputStream, limit: Int, what: String): ByteArray {
        val bytes = input.readNBytesCompat(limit + 1)
        if (bytes.size > limit) throw BackupFailure(BackupError.InvalidManifest("$what is too big"))
        return bytes
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 16 * 1024 * 1024
    }
}
