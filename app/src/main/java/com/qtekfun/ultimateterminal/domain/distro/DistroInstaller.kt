// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsCatalog
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsDownloader
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Installs a distro: resolve the official archive, download and verify it, unpack it and register
 * it. An install is transactional (SPEC RF-04): a failure, a cancellation or a full disk at any
 * step leaves neither a half-written root filesystem nor a database row behind.
 *
 * How: the distro is registered as `INSTALLING` first (so a clashing name fails before anything is
 * downloaded), the archive is unpacked in a private staging directory, and only a finished tree is
 * moved atomically to its final directory. If the process dies in between, [DistroManager.recoverInterrupted]
 * removes the leftovers on the next start.
 */
class DistroInstaller(
    private val catalog: RootfsCatalog,
    private val downloader: RootfsDownloader,
    private val extractor: RootfsExtractor,
    private val fileSystem: FileSystemRepository,
    private val distros: DistroRepository,
    /** The device's CPU architecture, or null when none of its ABIs is supported. */
    private val architecture: () -> Architecture?,
    private val supportedAbis: () -> List<String> = { emptyList() },
    private val newToken: () -> String = { UUID.randomUUID().toString() }
) {
    private val steps = InstallSteps(downloader, extractor, fileSystem, distros)

    /**
     * Cancelling the calling coroutine cleans up and rethrows, as usual with coroutines.
     *
     * Debian publishes its root filesystem on a branch that is rewritten often, so the hash read
     * from the index can be stale by the time the file is fetched (D-T06-3). That one error, and
     * only that one, resolves the catalog again and tries once more; every attempt is a whole
     * transaction, so the first one has already left nothing behind.
     */
    suspend fun install(
        request: InstallRequest,
        onProgress: (InstallProgress) -> Unit = {}
    ): InstallResult {
        val first = attempt(request, onProgress)
        return if (first.isStaleHash()) attempt(request, onProgress) else first
    }

    private fun InstallResult.isStaleHash(): Boolean {
        val error = (this as? InstallResult.Failure)?.error as? InstallError.Download
        return error?.error is RootfsError.HashMismatch
    }

    private suspend fun attempt(
        request: InstallRequest,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        val arch = architecture()
            ?: return failure(InstallError.UnsupportedArchitecture(supportedAbis()))
        onProgress(InstallProgress(InstallPhase.RESOLVING))
        return when (val resolved = catalog.resolve(request.family, arch)) {
            is RootfsResult.Failure -> failure(InstallError.Catalog(resolved.error))
            is RootfsResult.Success -> installFrom(request, resolved.value, onProgress)
        }
    }

    /** Checks the space, registers the distro and runs the install, cleaning up on any failure. */
    private suspend fun installFrom(
        request: InstallRequest,
        source: RootfsSource,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        val required = InstallSpace.requiredBytes(source.sizeBytes)
        val available = fileSystem.freeSpaceBytes()
        if (available < required) {
            return failure(InstallError.InsufficientSpace(required, available))
        }
        val token = newToken()
        val finalDirectory = DistroPaths.distroDirectory(token)
        val registered = distros.add(
            NewDistro(
                name = request.name,
                type = request.family.toType(),
                release = source.version,
                directory = finalDirectory,
                defaultUser = request.user
            )
        )
        return when (registered) {
            is Outcome.Failure -> failure(InstallError.InvalidRequest(registered.error))
            is Outcome.Success -> transactional(registered.value, source, token, onProgress)
        }
    }

    private suspend fun transactional(
        distro: Distro,
        source: RootfsSource,
        token: String,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        val staging = DistroPaths.stagingDirectory(token)
        var finished = false
        try {
            val result = run(distro, source, staging, onProgress)
            finished = result is InstallResult.Success
            return result
        } finally {
            // Runs on failure and on cancellation alike, so it must not be cancellable itself.
            withContext(NonCancellable) {
                fileSystem.deleteRecursively(staging)
                if (!finished) {
                    fileSystem.deleteRecursively(distro.directory)
                    distros.remove(distro.id)
                }
            }
        }
    }

    private suspend fun run(
        distro: Distro,
        source: RootfsSource,
        staging: FsPath,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        val archive = staging.child(ARCHIVE_NAME).path()
        val unpacked = staging.child(UNPACKED_NAME).path()
        return when (val prepared = fileSystem.createDirectories(staging)) {
            is Outcome.Failure -> steps.storageFailure(prepared.error)

            is Outcome.Success -> steps.download(source, archive, onProgress)
                ?: steps.extract(archive, unpacked, onProgress)
                ?: steps.publish(distro, unpacked, onProgress)
        }
    }

    private fun failure(error: InstallError) = InstallResult.Failure(error)

    /** The names are fixed constants under a UUID directory, so a failure here is a bug. */
    private fun Outcome<FsPath>.path(): FsPath = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> error("not a safe path: ${this.error}")
    }

    companion object {
        private const val ARCHIVE_NAME = "archive"

        /** The directory inside a distro's own directory that holds the root filesystem (proot's `-r`). */
        const val UNPACKED_NAME = "rootfs"
    }
}

internal fun DistroFamily.toType(): DistroType = when (this) {
    DistroFamily.DEBIAN -> DistroType.DEBIAN
    DistroFamily.UBUNTU -> DistroType.UBUNTU
    DistroFamily.ALPINE -> DistroType.ALPINE
}

internal fun DomainError.describe(): String = when (this) {
    is DomainError.Io -> message
    else -> toString()
}
