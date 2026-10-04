// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.StreamRootfsExtractor
import com.qtekfun.ultimateterminal.domain.distro.DistroPaths
import com.qtekfun.ultimateterminal.domain.distro.ExtractionResult
import com.qtekfun.ultimateterminal.domain.distro.InstallSpace
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Installs one distro from the stream of its part, as all-or-nothing as the installer: it is
 * registered, unpacked into a staging directory, checked against the manifest and only then moved
 * into place and marked ready. A failure or a cancellation at any point removes what was made.
 */
internal class DistroRestorer(
    private val distros: DistroRepository,
    private val fileSystem: FileSystemRepository,
    private val extractor: StreamRootfsExtractor,
    private val newToken: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun restore(
        part: ManifestPart,
        content: InputStream,
        onProgress: (Float?) -> Unit
    ): Distro {
        val required = InstallSpace.requiredBytes(part.size)
        val available = fileSystem.freeSpaceBytes()
        if (available < required) {
            throw BackupFailure(BackupError.InsufficientSpace(required, available))
        }
        val token = newToken()
        val final = DistroPaths.distroDirectory(token)
        val staging = DistroPaths.stagingDirectory(token)
        val distro = register(part.distro!!, final)
        var finished = false
        try {
            unpack(part, content, token, onProgress)
            publish(distro, token)
            finished = true
            return distro
        } finally {
            withContext(NonCancellable) {
                fileSystem.deleteRecursively(staging)
                if (!finished) {
                    fileSystem.deleteRecursively(final)
                    distros.remove(distro.id)
                }
            }
        }
    }

    /** The name is kept when it is free; otherwise "name (restored)" and a number are tried. */
    private suspend fun register(meta: DistroMeta, directory: FsPath): Distro {
        for (attempt in 0 until MAX_NAME_ATTEMPTS) {
            val name = when (attempt) {
                0 -> meta.name
                1 -> "${meta.name} (restored)"
                else -> "${meta.name} (restored $attempt)"
            }
            val added = distros.add(
                NewDistro(name, DistroType.valueOf(meta.type), meta.release, directory, meta.user)
            )
            when {
                added is Outcome.Success -> return added.value

                (added as Outcome.Failure).error !is DomainError.NameTaken ->
                    throw BackupFailure(BackupError.Io(added.error.toString()))
            }
        }
        throw BackupFailure(BackupError.Io("no free name for ${meta.name}"))
    }

    private suspend fun unpack(
        part: ManifestPart,
        content: InputStream,
        token: String,
        onProgress: (Float?) -> Unit
    ) {
        fileSystem.createDirectories(DistroPaths.stagingDirectory(token))
        val digest = DigestingInputStream(content)
        val extracted = extractor.extract(digest, part.size, rootfsOf(token), onProgress)
        // The extractor stops at the end of the tar; the rest of the part still has to be hashed.
        digest.drain()
        when {
            extracted is ExtractionResult.Failure -> throw BackupFailure(
                BackupError.Extraction(extracted.error)
            )

            digest.bytes != part.size || digest.sha256() != part.sha256 ->
                throw BackupFailure(BackupError.HashMismatch(part.name))
        }
    }

    private suspend fun publish(distro: Distro, token: String) {
        val moved = fileSystem.move(rootfsOf(token), distro.directory)
        val size = (fileSystem.sizeOf(distro.directory) as? Outcome.Success)?.value ?: 0L
        val ready = distros.updateState(distro.id, DistroState.READY, size)
        val failure = (moved as? Outcome.Failure) ?: (ready as? Outcome.Failure)
        failure?.let { throw BackupFailure(BackupError.Io(it.error.toString())) }
    }

    /** Where the archive is unpacked before it is moved to its place. */
    private fun rootfsOf(token: String): FsPath = DistroPaths.stagingDirectory("$token/$UNPACKED")

    private companion object {
        const val UNPACKED = "rootfs"
        const val MAX_NAME_ATTEMPTS = 20
    }
}
