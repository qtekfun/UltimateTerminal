// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.setup

import com.qtekfun.ultimateterminal.distro.DistroMessage
import com.qtekfun.ultimateterminal.distro.DistroUiState
import com.qtekfun.ultimateterminal.distro.InstallUiState
import com.qtekfun.ultimateterminal.domain.distro.DistroPaths
import com.qtekfun.ultimateterminal.domain.distro.InstallError
import com.qtekfun.ultimateterminal.domain.distro.InstallPhase
import com.qtekfun.ultimateterminal.domain.distro.InstallProgress
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.setup.SetupGate
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val distros = MutableStateFlow<List<Distro>>(emptyList())
    private val sessions = MutableStateFlow(false)
    private val madeDefault = mutableListOf<Long>()
    private var defaultSucceeds = true

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun distro(id: Long, state: DistroState, isDefault: Boolean = false) = Distro(
        id = id,
        name = "d$id",
        type = DistroType.ALPINE,
        release = "3",
        directory = DistroPaths.distroDirectory("t$id"),
        defaultUser = "root",
        state = state,
        sizeBytes = 1L,
        installedAt = Instant.EPOCH,
        isDefault = isDefault
    )

    private fun viewModel() = SetupViewModel(distros, sessions) { id ->
        madeDefault += id
        if (defaultSucceeds) {
            distros.value = distros.value.map { it.copy(isDefault = it.id == id) }
        }
        defaultSucceeds
    }

    @Test
    fun aFreshInstallShowsTheSetupAndSkippingClosesItForTheSessionOnly() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(SetupGate.UNDECIDED, model.gate.value)

        advanceUntilIdle()
        assertEquals(SetupGate.SHOWING, model.gate.value)

        model.skip()
        advanceUntilIdle()
        assertEquals(SetupGate.CLOSED, model.gate.value)

        // The choice is not stored anywhere: a new launch (a new ViewModel) shows it again.
        val nextLaunch = viewModel()
        advanceUntilIdle()
        assertEquals(SetupGate.SHOWING, nextLaunch.gate.value)
    }

    @Test
    fun anAppReopenedWithItsTabsOrWithADistroNeverShowsTheSetup() = runTest(dispatcher) {
        sessions.value = true
        val withTabs = viewModel()
        advanceUntilIdle()
        assertEquals(SetupGate.CLOSED, withTabs.gate.value)

        sessions.value = false
        distros.value = listOf(distro(1, DistroState.READY, isDefault = true))
        val withDistro = viewModel()
        advanceUntilIdle()
        assertEquals(SetupGate.CLOSED, withDistro.gate.value)
    }

    @Test
    fun whenTheInstallFinishesTheNewDistroBecomesTheDefaultBeforeTheSetupCloses() =
        runTest(dispatcher) {
            // An earlier failed row holds the default flag, as after an interrupted install.
            distros.value = listOf(distro(1, DistroState.FAILED, isDefault = true))
            val model = viewModel()
            advanceUntilIdle()
            assertEquals(SetupGate.SHOWING, model.gate.value)

            distros.value += distro(2, DistroState.INSTALLING)
            advanceUntilIdle()
            assertEquals(SetupGate.SHOWING, model.gate.value)

            distros.value = distros.value.map {
                if (it.id == 2L) it.copy(state = DistroState.READY) else it
            }
            advanceUntilIdle()

            assertEquals(listOf(2L), madeDefault)
            assertEquals(SetupGate.CLOSED, model.gate.value)
        }

    @Test
    fun aDefaultThatCannotBeSetDoesNotLeaveTheUserOnTheSetup() = runTest(dispatcher) {
        defaultSucceeds = false
        val model = viewModel()
        advanceUntilIdle()

        distros.value = listOf(distro(5, DistroState.READY))
        advanceUntilIdle()

        assertEquals(listOf(5L), madeDefault)
        assertEquals(SetupGate.CLOSED, model.gate.value)
    }

    @Test
    fun theStepFollowsTheInstallAndAFailureLeavesTheFormToRetry() {
        val install = InstallUiState("Alpine", InstallProgress(InstallPhase.DOWNLOADING, 0.5f))
        val installing = DistroUiState(ready = true, installing = install)
        assertEquals(SetupStep.Installing(install), setupStepOf(installing))

        val error = InstallError.Storage("disk")
        val failed = DistroUiState(ready = true, message = DistroMessage.InstallFailed(error))
        assertEquals(SetupStep.Choose(error), setupStepOf(failed))

        // Other messages are not an install failure.
        val installed = DistroUiState(ready = true, message = DistroMessage.Installed("Alpine"))
        assertEquals(SetupStep.Choose(null), setupStepOf(installed))
        assertEquals(SetupStep.Choose(null), setupStepOf(DistroUiState()))
    }
}
