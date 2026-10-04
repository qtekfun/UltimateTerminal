// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.distro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.distro.DistroInstaller
import com.qtekfun.ultimateterminal.domain.distro.DistroManager
import com.qtekfun.ultimateterminal.domain.distro.InstallError
import com.qtekfun.ultimateterminal.domain.distro.InstallPhase
import com.qtekfun.ultimateterminal.domain.distro.InstallProgress
import com.qtekfun.ultimateterminal.domain.distro.InstallRequest
import com.qtekfun.ultimateterminal.domain.distro.InstallResult
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user is told after an action; the screen turns it into text. */
sealed interface DistroMessage {
    data class Installed(val name: String) : DistroMessage

    data class InstallFailed(val error: InstallError) : DistroMessage

    data class ActionFailed(val error: DomainError) : DistroMessage
}

data class InstallUiState(val name: String, val progress: InstallProgress)

data class DistroUiState(
    val distros: List<Distro> = emptyList(),
    /** False until the leftovers of an interrupted install have been cleaned up. */
    val ready: Boolean = false,
    val installing: InstallUiState? = null,
    val message: DistroMessage? = null
)

/**
 * Drives the distro screen. The install runs in this ViewModel's scope, so it survives a rotation
 * but not the app being closed; T08 moves it to the foreground service. If the process dies
 * mid-install, [DistroManager.recoverInterrupted] cleans up at the next start.
 */
@HiltViewModel
class DistroViewModel @Inject constructor(
    private val installer: DistroInstaller,
    private val manager: DistroManager
) : ViewModel() {
    private val state = MutableStateFlow(DistroUiState())
    val uiState: StateFlow<DistroUiState> = state.asStateFlow()

    private var installJob: Job? = null

    init {
        viewModelScope.launch {
            // Before anything else: an install that died with the process must not look alive.
            manager.recoverInterrupted()
            state.update { it.copy(ready = true) }
            manager.observe().collect { list -> state.update { it.copy(distros = list) } }
        }
    }

    fun install(family: DistroFamily, name: String, user: String) {
        if (installJob?.isActive == true) return
        val request = InstallRequest(family, name.trim(), user.trim())
        state.update {
            it.copy(
                installing = InstallUiState(request.name, InstallProgress(InstallPhase.RESOLVING)),
                message = null
            )
        }
        installJob = viewModelScope.launch {
            val result = try {
                installer.install(request) { progress ->
                    state.update { current ->
                        current.copy(installing = current.installing?.copy(progress = progress))
                    }
                }
            } finally {
                state.update { it.copy(installing = null) }
            }
            state.update {
                it.copy(
                    message = when (result) {
                        is InstallResult.Success -> DistroMessage.Installed(result.distro.name)
                        is InstallResult.Failure -> DistroMessage.InstallFailed(result.error)
                    }
                )
            }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
    }

    fun rename(id: Long, name: String) = act { manager.rename(id, name.trim()) }

    fun setDefault(id: Long) = act { manager.setDefault(id) }

    fun duplicate(id: Long, name: String) = act { manager.duplicate(id, name.trim()) }

    fun delete(id: Long) = act { manager.delete(id) }

    fun dismissMessage() = state.update { it.copy(message = null) }

    private fun act(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            val outcome = block()
            if (outcome is Outcome.Failure) {
                state.update { it.copy(message = DistroMessage.ActionFailed(outcome.error)) }
            }
        }
    }
}
