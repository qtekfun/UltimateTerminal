// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsDownloader
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource

/**
 * The mechanical steps of one install attempt: download, unpack, publish. Each returns null (or a
 * ready distro) on success and an [InstallResult.Failure] otherwise; none of them cleans up, since
 * [DistroInstaller] owns the transaction and removes whatever a failed attempt left.
 */
internal class InstallSteps(
    private val downloader: RootfsDownloader,
    private val extractor: RootfsExtractor,
    private val fileSystem: FileSystemRepository,
    private val distros: DistroRepository
) {
    /** Null when the archive was downloaded and verified. */
    suspend fun download(
        source: RootfsSource,
        archive: FsPath,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult? {
        onProgress(InstallProgress(InstallPhase.DOWNLOADING, 0f))
        var verifying = false
        val downloaded = downloader.download(source, fileSystem.absolutePathOf(archive)) {
            val fraction = it.fraction
            // The downloader hashes the file as soon as the last byte arrives.
            if (fraction == 1f && !verifying) {
                verifying = true
                onProgress(InstallProgress(InstallPhase.VERIFYING))
            } else if (!verifying) {
                onProgress(InstallProgress(InstallPhase.DOWNLOADING, fraction))
            }
        }
        return (downloaded as? RootfsResult.Failure)?.let {
            failure(InstallError.Download(it.error))
        }
    }

    /** Null when the archive was unpacked into [unpacked]. */
    suspend fun extract(
        archive: FsPath,
        unpacked: FsPath,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult? {
        onProgress(InstallProgress(InstallPhase.EXTRACTING, 0f))
        val extracted = extractor.extract(archive, unpacked) {
            onProgress(InstallProgress(InstallPhase.EXTRACTING, it))
        }
        return (extracted as? ExtractionResult.Failure)
            ?.let { failure(InstallError.Extraction(it.error)) }
    }

    /** Moves the finished tree into place and marks the distro ready. */
    suspend fun publish(
        distro: Distro,
        unpacked: FsPath,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult {
        onProgress(InstallProgress(InstallPhase.FINALIZING))
        val ready = fileSystem.move(unpacked, distro.directory).flatMap {
            val size = (fileSystem.sizeOf(distro.directory) as? Outcome.Success)?.value ?: 0L
            distros.updateState(distro.id, DistroState.READY, size)
        }
        return when (ready) {
            is Outcome.Failure -> storageFailure(ready.error)

            is Outcome.Success -> distros.get(distro.id)?.let { InstallResult.Success(it) }
                ?: failure(InstallError.Storage("the distro vanished after the install"))
        }
    }

    fun failure(error: InstallError) = InstallResult.Failure(error)

    fun storageFailure(error: DomainError) = failure(InstallError.Storage(error.describe()))
}
