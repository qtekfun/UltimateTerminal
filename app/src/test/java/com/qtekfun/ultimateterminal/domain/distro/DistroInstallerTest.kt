// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.DownloadProgress
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsCatalog
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.fakes.ALPINE_SOURCE
import com.qtekfun.ultimateterminal.fakes.FailingFileSystem
import com.qtekfun.ultimateterminal.fakes.FakeCatalog
import com.qtekfun.ultimateterminal.fakes.FakeDistroRepository
import com.qtekfun.ultimateterminal.fakes.FakeDownloader
import com.qtekfun.ultimateterminal.fakes.FakeExtractor
import com.qtekfun.ultimateterminal.fakes.FlakyDistroRepository
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import com.qtekfun.ultimateterminal.fakes.SequenceCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DistroInstallerTest {
    private val storage = InMemoryFileSystemRepository()
    private val repository = FakeDistroRepository()
    private val catalog = FakeCatalog()
    private val downloader = FakeDownloader(storage)
    private val extractor = FakeExtractor(storage)
    private var tokens = 0

    private fun installer(
        fileSystem: FileSystemRepository = storage,
        distros: DistroRepository = repository,
        architecture: Architecture? = Architecture.ARM64
    ) = DistroInstaller(
        catalog = catalog,
        downloader = downloader,
        extractor = extractor,
        fileSystem = fileSystem,
        distros = distros,
        architecture = { architecture },
        supportedAbis = { listOf("mips") },
        newToken = { "token${++tokens}" }
    )

    private val request = InstallRequest(DistroFamily.ALPINE, "Alpine")

    private suspend fun rows() = repository.observeAll().first()

    private suspend fun assertNothingLeftBehind() {
        assertEquals(emptyList<Any>(), rows())
        assertFalse(storage.exists(DistroPaths.stagingDirectory("token1")))
        assertFalse(storage.exists(DistroPaths.distroDirectory("token1")))
    }

    @Test
    fun aSuccessfulInstallRegistersAReadyDistroWithItsFiles() = runTest {
        val result = installer().install(request)

        val distro = (result as InstallResult.Success).distro
        assertEquals(DistroState.READY, distro.state)
        assertEquals(DistroType.ALPINE, distro.type)
        assertEquals("3.22.1", distro.release)
        assertEquals("distros/token1", distro.directory.value)
        assertEquals(true, distro.isDefault)
        assertEquals("root", distro.defaultUser)
        assertEquals("NAME=Fake", String(storage.readFile("distros/token1/etc/os-release")!!))
        assertEquals("sh", String(storage.readFile("distros/token1/bin/sh")!!))
        assertEquals(("NAME=Fake".length + "sh".length).toLong(), distro.sizeBytes)
        assertFalse(storage.exists(DistroPaths.stagingDirectory("token1")))
    }

    @Test
    fun theInstallReportsEachPhaseInOrder() = runTest {
        val phases = mutableListOf<InstallPhase>()

        installer().install(request) { phases.add(it.phase) }

        assertEquals(
            listOf(
                InstallPhase.RESOLVING,
                InstallPhase.DOWNLOADING,
                InstallPhase.DOWNLOADING,
                InstallPhase.DOWNLOADING,
                InstallPhase.VERIFYING,
                InstallPhase.EXTRACTING,
                InstallPhase.EXTRACTING,
                InstallPhase.EXTRACTING,
                InstallPhase.FINALIZING
            ),
            phases
        )
    }

    @Test
    fun downloadProgressWithAnUnknownTotalStillReportsDownloading() = runTest {
        downloader.progress = listOf(DownloadProgress(10, null), DownloadProgress(20, null))
        val seen = mutableListOf<InstallProgress>()

        installer().install(request) { seen.add(it) }

        val downloading = seen.filter { it.phase == InstallPhase.DOWNLOADING }
        assertEquals(listOf(0f, null, null), downloading.map { it.fraction })
    }

    @Test
    fun aTakenNameFailsBeforeAnythingIsDownloaded() = runTest {
        installer().install(request)
        downloader.calls = 0

        val second = installer().install(request.copy(name = "ALPINE"))

        val error = (second as InstallResult.Failure).error
        assertInstanceOf(InstallError.InvalidRequest::class.java, error)
        assertEquals(DomainError.NameTaken("ALPINE"), (error as InstallError.InvalidRequest).error)
        assertEquals(0, downloader.calls)
        // The first install is untouched.
        assertEquals(1, rows().size)
        assertTrue(storage.exists(DistroPaths.distroDirectory("token1")))
    }

    @Test
    fun anInvalidNameIsRefused() = runTest {
        val result = installer().install(request.copy(name = ""))

        assertInstanceOf(
            InstallError.InvalidRequest::class.java,
            (result as InstallResult.Failure).error
        )
        assertEquals(0, downloader.calls)
    }

    @Test
    fun aDeviceWithoutASupportedArchitectureCannotInstall() = runTest {
        val result = installer(architecture = null).install(request)

        assertEquals(
            InstallResult.Failure(InstallError.UnsupportedArchitecture(listOf("mips"))),
            result
        )
        assertEquals(0, catalog.calls)
    }

    @Test
    fun aCatalogFailureLeavesNothing() = runTest {
        catalog.result = RootfsResult.Failure(RootfsError.CatalogUnavailable("offline"))

        val result = installer().install(request)

        assertEquals(
            InstallResult.Failure(InstallError.Catalog(RootfsError.CatalogUnavailable("offline"))),
            result
        )
        assertNothingLeftBehind()
    }

    @Test
    fun notEnoughFreeSpaceIsRefusedBeforeTheDownload() = runTest {
        val tight = InMemoryFileSystemRepository(freeSpace = 1_000_000L)
        val result = DistroInstaller(
            catalog,
            FakeDownloader(tight),
            FakeExtractor(tight),
            tight,
            repository,
            architecture = { Architecture.ARM64 },
            newToken = { "token1" }
        ).install(request)

        val error = (result as InstallResult.Failure).error as InstallError.InsufficientSpace
        assertEquals(InstallSpace.requiredBytes(3_000_000L), error.requiredBytes)
        assertEquals(1_000_000L, error.availableBytes)
        assertEquals(emptyList<Any>(), rows())
    }

    @Test
    fun aFailedDownloadRemovesTheRowAndTheStagingFiles() = runTest {
        downloader.result = RootfsResult.Failure(RootfsError.HashMismatch("a", "b"))

        val result = installer().install(request)

        assertEquals(
            InstallResult.Failure(InstallError.Download(RootfsError.HashMismatch("a", "b"))),
            result
        )
        assertNothingLeftBehind()
    }

    @Test
    fun aRefusedArchiveLeavesNothingAndTheNameCanBeUsedAgain() = runTest {
        extractor.result =
            ExtractionResult.Failure(ExtractionError.UnsafeEntry("../x", "contains \"..\""))

        val failed = installer().install(request)

        assertInstanceOf(
            InstallError.Extraction::class.java,
            (failed as InstallResult.Failure).error
        )
        assertNothingLeftBehind()
        // Idempotent: the same request works once the problem is gone.
        extractor.result = ExtractionResult.Success(ExtractionStats(1, 1, 0))
        val retry = installer().install(request)
        assertInstanceOf(InstallResult.Success::class.java, retry)
        assertEquals(1, rows().size)
    }

    @Test
    fun aFailureWhileCreatingTheStagingDirectoryIsAStorageError() = runTest {
        val broken = FailingFileSystem(storage, failCreate = true)

        val result = installer(fileSystem = broken).install(request)

        assertEquals(
            InstallResult.Failure(InstallError.Storage("disk full")),
            result
        )
        assertEquals(emptyList<Any>(), rows())
    }

    @Test
    fun aFailureMovingTheFinishedTreeRemovesEverything() = runTest {
        val broken = FailingFileSystem(storage, failMove = true)

        val result = installer(fileSystem = broken).install(request)

        assertEquals(InstallResult.Failure(InstallError.Storage("cannot rename")), result)
        assertNothingLeftBehind()
    }

    @Test
    fun aDatabaseFailureAtTheEndRemovesTheFilesToo() = runTest {
        val flaky = FlakyDistroRepository(repository, failReady = true)

        val result = installer(distros = flaky).install(request)

        assertEquals(InstallResult.Failure(InstallError.Storage("database is locked")), result)
        assertNothingLeftBehind()
    }

    @Test
    fun aDistroThatVanishesAfterTheInstallIsReported() = runTest {
        val flaky = FlakyDistroRepository(repository, hideAfterReady = true)

        val result = installer(distros = flaky).install(request)

        assertInstanceOf(InstallError.Storage::class.java, (result as InstallResult.Failure).error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellingHalfwayThroughLeavesNothingBehind() = runTest {
        val gate = CompletableDeferred<Unit>()
        extractor.gate = gate
        var result: InstallResult? = null
        val job = launch { result = installer().install(request) }
        extractor.started.await()
        // Files already exist in staging and the row is registered: this is the dangerous moment.
        assertTrue(storage.exists(DistroPaths.stagingDirectory("token1")))
        assertEquals(1, rows().size)

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertNull(result)
        assertNothingLeftBehind()
    }

    @Test
    fun theSameNameCanBeInstalledAgainAfterACancellation() = runTest {
        val gate = CompletableDeferred<Unit>()
        extractor.gate = gate
        val job = launch { installer().install(request) }
        extractor.started.await()
        job.cancel()
        job.join()
        extractor.gate = null

        val result = installer().install(request)

        assertInstanceOf(InstallResult.Success::class.java, result)
        assertEquals(listOf("Alpine"), rows().map { it.name })
    }

    @Test
    fun twoInstallsDoNotShareADirectory() = runTest {
        val first = installer().install(request) as InstallResult.Success
        val second = installer().install(request.copy(name = "Alpine 2")) as InstallResult.Success

        assertEquals("distros/token1", first.distro.directory.value)
        assertEquals("distros/token2", second.distro.directory.value)
        assertEquals(false, second.distro.isDefault)
    }

    @Test
    fun everyFamilyMapsToItsType() = runTest {
        assertEquals(DistroType.DEBIAN, DistroFamily.DEBIAN.toType())
        assertEquals(DistroType.UBUNTU, DistroFamily.UBUNTU.toType())
        assertEquals(DistroType.ALPINE, DistroFamily.ALPINE.toType())
    }

    @Test
    fun describeKeepsTheIoMessageAndFallsBackToTheErrorText() {
        assertEquals("boom", DomainError.Io("boom").describe())
        assertEquals(DomainError.NotFound.toString(), DomainError.NotFound.describe())
    }

    @Test
    fun aKnownTotalReportsItsFractionAndSwitchesToVerifyingOnlyOnce() = runTest {
        downloader.progress = listOf(
            DownloadProgress(0, 100),
            DownloadProgress(100, 100),
            DownloadProgress(100, 100)
        )
        val seen = mutableListOf<InstallProgress>()

        installer().install(request) { seen.add(it) }

        assertEquals(
            listOf(
                InstallProgress(InstallPhase.DOWNLOADING, 0f),
                InstallProgress(InstallPhase.DOWNLOADING, 0f),
                InstallProgress(InstallPhase.VERIFYING)
            ),
            seen.filter {
                it.phase == InstallPhase.DOWNLOADING || it.phase == InstallPhase.VERIFYING
            }
        )
    }

    @Test
    fun theSizeIsZeroWhenTheTreeCannotBeMeasured() = runTest {
        val unmeasurable = object : FileSystemRepository by storage {
            override suspend fun sizeOf(path: FsPath): Outcome<Long> =
                Outcome.Failure(DomainError.Io("cannot measure"))
        }

        val result = installer(fileSystem = unmeasurable).install(request)

        assertEquals(0L, (result as InstallResult.Success).distro.sizeBytes)
    }

    private fun debianInstaller(catalog: RootfsCatalog) = DistroInstaller(
        catalog = catalog,
        downloader = downloader,
        extractor = extractor,
        fileSystem = storage,
        distros = repository,
        architecture = { Architecture.ARM64 },
        newToken = { "token${++tokens}" }
    )

    private val oldDebian = ALPINE_SOURCE.copy(
        family = DistroFamily.DEBIAN,
        sha256 = "1".repeat(64)
    )
    private val newDebian = ALPINE_SOURCE.copy(
        family = DistroFamily.DEBIAN,
        sha256 = "2".repeat(64)
    )
    private val stale = RootfsResult.Failure(
        RootfsError.HashMismatch("1".repeat(64), "2".repeat(64))
    )

    @Test
    fun aHashMismatchResolvesTheCatalogAgainAndRetriesOnce() = runTest {
        // Debian's branch is rewritten between the manifest and the file (D-T06-3).
        val sequence = SequenceCatalog(
            listOf(RootfsResult.Success(oldDebian), RootfsResult.Success(newDebian))
        )
        downloader.results = listOf(stale, RootfsResult.Success(Unit))

        val result = debianInstaller(sequence).install(request.copy(family = DistroFamily.DEBIAN))

        assertInstanceOf(InstallResult.Success::class.java, result)
        assertEquals(2, sequence.calls)
        assertEquals(listOf(oldDebian, newDebian), downloader.sourcesSeen)
        // The first attempt (token1) left nothing behind; the distro is the second one (token2).
        assertEquals(listOf("distros/token2"), rows().map { it.directory.value })
        assertFalse(storage.exists(DistroPaths.distroDirectory("token1")))
        assertFalse(storage.exists(DistroPaths.stagingDirectory("token1")))
        assertFalse(storage.exists(DistroPaths.stagingDirectory("token2")))
    }

    @Test
    fun aSecondHashMismatchIsReportedAndNothingIsKept() = runTest {
        val sequence = SequenceCatalog(
            listOf(RootfsResult.Success(oldDebian), RootfsResult.Success(newDebian))
        )
        downloader.results = listOf(stale, stale)

        val result = debianInstaller(sequence).install(request.copy(family = DistroFamily.DEBIAN))

        assertEquals(InstallResult.Failure(InstallError.Download(stale.error)), result)
        assertEquals(2, downloader.calls)
        assertNothingLeftBehind()
    }

    @Test
    fun otherDownloadErrorsAreNotRetried() = runTest {
        val sequence = SequenceCatalog(listOf(RootfsResult.Success(oldDebian)))
        downloader.results = listOf(RootfsResult.Failure(RootfsError.Network("reset")))

        val result = debianInstaller(sequence).install(request.copy(family = DistroFamily.DEBIAN))

        assertEquals(
            InstallResult.Failure(InstallError.Download(RootfsError.Network("reset"))),
            result
        )
        assertEquals(1, downloader.calls)
        assertEquals(1, sequence.calls)
    }

    @Test
    fun theRetryAlsoFailsCleanlyWhenTheCatalogIsGoneTheSecondTime() = runTest {
        val unavailable = RootfsResult.Failure(RootfsError.CatalogUnavailable("offline"))
        val sequence = SequenceCatalog(listOf(RootfsResult.Success(oldDebian), unavailable))
        downloader.results = listOf(stale)

        val result = debianInstaller(sequence).install(request.copy(family = DistroFamily.DEBIAN))

        assertEquals(InstallResult.Failure(InstallError.Catalog(unavailable.error)), result)
        assertNothingLeftBehind()
    }
}
