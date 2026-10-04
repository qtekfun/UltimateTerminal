// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.fakes

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.distro.ExtractionResult
import com.qtekfun.ultimateterminal.domain.distro.ExtractionStats
import com.qtekfun.ultimateterminal.domain.distro.RootfsExtractor
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.DownloadProgress
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsCatalog
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsDownloader
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import kotlinx.coroutines.CompletableDeferred

val ALPINE_SOURCE = RootfsSource(
    family = DistroFamily.ALPINE,
    architecture = Architecture.ARM64,
    version = "3.22.1",
    url = "https://example.org/alpine.tar.gz",
    sha256 = "a".repeat(64),
    sizeBytes = 3_000_000L
)

class FakeCatalog(var result: RootfsResult<RootfsSource> = RootfsResult.Success(ALPINE_SOURCE)) :
    RootfsCatalog {
    var calls = 0

    override suspend fun resolve(
        family: DistroFamily,
        architecture: Architecture
    ): RootfsResult<RootfsSource> {
        calls++
        return result
    }
}

/** A catalog that gives a different answer on each call, to test what the installer re-resolves. */
class SequenceCatalog(private val answers: List<RootfsResult<RootfsSource>>) : RootfsCatalog {
    var calls = 0
        private set

    override suspend fun resolve(
        family: DistroFamily,
        architecture: Architecture
    ): RootfsResult<RootfsSource> = answers[minOf(calls++, answers.lastIndex)]
}

/** Writes a fake archive where the installer asks for it, like the real downloader does. */
class FakeDownloader(private val fileSystem: InMemoryFileSystemRepository) : RootfsDownloader {
    var result: RootfsResult<Unit> = RootfsResult.Success(Unit)

    /** When set, the n-th call (from 0) answers with the n-th result instead of [result]. */
    var results: List<RootfsResult<Unit>>? = null
    val sourcesSeen = mutableListOf<RootfsSource>()
    var progress: List<DownloadProgress> = listOf(
        DownloadProgress(0, 100),
        DownloadProgress(50, 100),
        DownloadProgress(100, 100)
    )
    var calls = 0

    override suspend fun download(
        source: RootfsSource,
        destinationPath: String,
        onProgress: (DownloadProgress) -> Unit
    ): RootfsResult<Unit> {
        val answer = results?.let { it[minOf(calls, it.lastIndex)] } ?: result
        calls++
        sourcesSeen += source
        progress.forEach(onProgress)
        if (answer is RootfsResult.Success) {
            fileSystem.putFile(destinationPath.removePrefix("/storage/"), "archive".toByteArray())
        }
        return answer
    }
}

/**
 * Writes a small root filesystem into the destination. [gate], when set, holds the extraction
 * until the test releases it, so a test can cancel an install halfway through.
 */
class FakeExtractor(private val fileSystem: InMemoryFileSystemRepository) : RootfsExtractor {
    var result: ExtractionResult = ExtractionResult.Success(ExtractionStats(2, 10, 0))
    var gate: CompletableDeferred<Unit>? = null
    var started = CompletableDeferred<Unit>()
    var calls = 0

    override suspend fun extract(
        archive: FsPath,
        destination: FsPath,
        onProgress: (Float?) -> Unit
    ): ExtractionResult {
        calls++
        onProgress(0.5f)
        fileSystem.putFile("${destination.value}/etc/os-release", "NAME=Fake".toByteArray())
        started.complete(Unit)
        gate?.await()
        if (result is ExtractionResult.Success) {
            fileSystem.putFile("${destination.value}/bin/sh", "sh".toByteArray())
        }
        onProgress(1f)
        return result
    }
}

/** A file system whose chosen operations fail, to test what the callers clean up. */
class FailingFileSystem(
    private val inner: InMemoryFileSystemRepository,
    var failCreate: Boolean = false,
    var failMove: Boolean = false,
    var failCopy: Boolean = false,
    var failDelete: Boolean = false
) : FileSystemRepository by inner {
    override suspend fun createDirectories(path: FsPath): Outcome<Unit> = if (failCreate) {
        Outcome.Failure(
            DomainError.Io("disk full")
        )
    } else {
        inner.createDirectories(path)
    }

    override suspend fun move(from: FsPath, to: FsPath): Outcome<Unit> =
        if (failMove) Outcome.Failure(DomainError.Io("cannot rename")) else inner.move(from, to)

    override suspend fun copyRecursively(from: FsPath, to: FsPath): Outcome<Unit> = if (failCopy) {
        // A real copy that dies halfway leaves nothing, but the caller must still clean up.
        Outcome.Failure(DomainError.Io("copy interrupted"))
    } else {
        inner.copyRecursively(from, to)
    }

    override suspend fun deleteRecursively(path: FsPath): Outcome<Unit> = if (failDelete) {
        Outcome.Failure(
            DomainError.Io("cannot delete")
        )
    } else {
        inner.deleteRecursively(path)
    }
}

/** A distro repository whose chosen calls fail or lie, for the paths a healthy one never takes. */
class FlakyDistroRepository(
    private val inner: FakeDistroRepository,
    var failReady: Boolean = false,
    var hideAfterReady: Boolean = false,
    /** Distros that were already there are never hidden; only ones that became READY later. */
    private val untouched: Set<Long> = emptySet()
) : DistroRepository by inner {
    override suspend fun updateState(
        id: Long,
        state: DistroState,
        sizeBytes: Long?
    ): Outcome<Unit> = if (failReady && state == DistroState.READY) {
        Outcome.Failure(DomainError.Io("database is locked"))
    } else {
        inner.updateState(id, state, sizeBytes)
    }

    override suspend fun get(id: Long): Distro? =
        if (hideAfterReady && id !in untouched && inner.get(id)?.state == DistroState.READY) {
            null
        } else {
            inner.get(id)
        }
}
