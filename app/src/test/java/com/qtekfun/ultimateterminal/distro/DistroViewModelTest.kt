// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.distro

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.distro.DistroInstaller
import com.qtekfun.ultimateterminal.domain.distro.DistroManager
import com.qtekfun.ultimateterminal.domain.distro.DistroPaths
import com.qtekfun.ultimateterminal.domain.distro.InstallError
import com.qtekfun.ultimateterminal.domain.distro.InstallPhase
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.fakes.FakeCatalog
import com.qtekfun.ultimateterminal.fakes.FakeDistroRepository
import com.qtekfun.ultimateterminal.fakes.FakeDownloader
import com.qtekfun.ultimateterminal.fakes.FakeExtractor
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DistroViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val storage = InMemoryFileSystemRepository()
    private val repository = FakeDistroRepository()
    private val catalog = FakeCatalog()
    private val downloader = FakeDownloader(storage)
    private val extractor = FakeExtractor(storage)
    private var tokens = 0

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): DistroViewModel {
        val installer = DistroInstaller(
            catalog,
            downloader,
            extractor,
            storage,
            repository,
            architecture = { Architecture.ARM64 },
            newToken = { "token${++tokens}" }
        )
        return DistroViewModel(installer, DistroManager(repository, storage) { "copy${++tokens}" })
    }

    private fun TestScope.started(): DistroViewModel = viewModel().also { advanceUntilIdle() }

    @Test
    fun theScreenIsReadyOnlyAfterTheLeftoversOfAnInterruptedInstallAreCleaned() =
        runTest(dispatcher) {
            val broken = repository.add(
                NewDistro(
                    "Old",
                    DistroType.ALPINE,
                    "3",
                    DistroPaths.distroDirectory("old")
                )
            ) as Outcome.Success
            storage.putFile("distros/old/etc/os-release")
            val model = viewModel()
            assertEquals(false, model.uiState.value.ready)

            advanceUntilIdle()

            assertEquals(true, model.uiState.value.ready)
            assertEquals(emptyList<Any>(), model.uiState.value.distros)
            assertNull(repository.get(broken.value.id))
        }

    @Test
    fun anInstallShowsItsProgressThenTheResult() = runTest(dispatcher) {
        // Held at the extraction step so the progress card can be observed.
        val gate = CompletableDeferred<Unit>()
        extractor.gate = gate
        val model = started()

        model.install(DistroFamily.ALPINE, " Alpine ", " root ")
        runCurrent()

        // The name is trimmed and the card shows the phase the install is in.
        assertEquals("Alpine", model.uiState.value.installing?.name)
        assertEquals(InstallPhase.EXTRACTING, model.uiState.value.installing?.progress?.phase)
        assertNull(model.uiState.value.message)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = model.uiState.value
        assertNull(state.installing)
        assertEquals(DistroMessage.Installed("Alpine"), state.message)
        assertEquals(listOf("Alpine"), state.distros.map { it.name })
        assertEquals(DistroState.READY, state.distros.single().state)
        assertEquals("root", state.distros.single().defaultUser)
    }

    @Test
    fun aSecondInstallWhileOneRunsIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        extractor.gate = gate
        val model = started()

        model.install(DistroFamily.ALPINE, "Alpine", "root")
        runCurrent()
        model.install(DistroFamily.DEBIAN, "Debian", "root")
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, downloader.calls)
        assertEquals(listOf("Alpine"), model.uiState.value.distros.map { it.name })
    }

    @Test
    fun aFailedInstallExplainsWhatWentWrong() = runTest(dispatcher) {
        catalog.result = RootfsResult.Failure(RootfsError.CatalogUnavailable("offline"))
        val model = started()

        model.install(DistroFamily.ALPINE, "Alpine", "root")
        advanceUntilIdle()

        val message = model.uiState.value.message as DistroMessage.InstallFailed
        assertEquals(
            InstallError.Catalog(RootfsError.CatalogUnavailable("offline")),
            message.error
        )
        assertNull(model.uiState.value.installing)
        assertEquals(emptyList<Any>(), model.uiState.value.distros)
    }

    @Test
    fun cancellingAnInstallLeavesNothingAndClearsTheCard() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        extractor.gate = gate
        val model = started()
        model.install(DistroFamily.ALPINE, "Alpine", "root")
        runCurrent()
        assertEquals(InstallPhase.EXTRACTING, model.uiState.value.installing?.progress?.phase)

        model.cancelInstall()
        advanceUntilIdle()

        assertNull(model.uiState.value.installing)
        assertEquals(emptyList<Any>(), model.uiState.value.distros)
        assertTrue(!storage.exists(DistroPaths.stagingDirectory("token1")))
    }

    @Test
    fun cancellingWithNothingRunningDoesNothing() = runTest(dispatcher) {
        val model = started()

        model.cancelInstall()
        advanceUntilIdle()

        assertNull(model.uiState.value.message)
    }

    @Test
    fun managementActionsReachTheDistrosAndReportFailures() = runTest(dispatcher) {
        val model = started()
        model.install(DistroFamily.ALPINE, "Alpine", "root")
        advanceUntilIdle()
        model.dismissMessage()
        val alpine = model.uiState.value.distros.single()

        model.rename(alpine.id, " Work ")
        advanceUntilIdle()
        assertEquals(listOf("Work"), model.uiState.value.distros.map { it.name })

        model.duplicate(alpine.id, "work")
        advanceUntilIdle()
        val taken = model.uiState.value.message as DistroMessage.ActionFailed
        assertEquals(DomainError.NameTaken("work"), taken.error)
        model.dismissMessage()
        assertNull(model.uiState.value.message)

        model.duplicate(alpine.id, "Copy")
        advanceUntilIdle()
        assertEquals(listOf("Copy", "Work"), model.uiState.value.distros.map { it.name })

        val copy = model.uiState.value.distros.first { it.name == "Copy" }
        model.setDefault(copy.id)
        advanceUntilIdle()
        assertTrue(model.uiState.value.distros.first { it.name == "Copy" }.isDefault)

        model.delete(alpine.id)
        advanceUntilIdle()
        assertEquals(listOf("Copy"), model.uiState.value.distros.map { it.name })
    }
}
