// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ssh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.data.proot.DistroLaunchFactory
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import com.qtekfun.ultimateterminal.domain.ssh.SshConnector
import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyService
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.terminal.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user is told after an action; the screens turn it into text. */
sealed interface SshMessage {
    data class KeyAdded(val name: String) : SshMessage

    data class Failed(val error: SshError) : SshMessage

    data class HostFailed(val error: DomainError) : SshMessage

    data object PrivateKeySaved : SshMessage

    data object PublicKeyCopied : SshMessage
}

data class SshUiState(
    val hosts: List<SshHost> = emptyList(),
    val keys: List<SshKeyInfo> = emptyList(),
    /** Only distros that finished installing: `ssh` cannot run in a half-installed one. */
    val distros: List<Distro> = emptyList(),
    /** A connection is being prepared; the screen shows progress and blocks a second tap. */
    val busy: Boolean = false,
    val message: SshMessage? = null
)

/**
 * Drives the hosts screen: saved servers and the one-touch connection that opens a tab running
 * `ssh` in a distro. Keys are managed by [SshKeysViewModel]; only their public parts are read here,
 * to offer them in the host form.
 */
@HiltViewModel
class SshViewModel @Inject constructor(
    private val hostRepository: SshHostRepository,
    distroRepository: DistroRepository,
    keyService: SshKeyService,
    private val connector: SshConnector,
    private val launchFactory: DistroLaunchFactory,
    private val sessions: SessionManager
) : ViewModel() {
    private val local = MutableStateFlow(SshUiState())

    val uiState: StateFlow<SshUiState> = combine(
        local,
        hostRepository.observeAll(),
        keyService.observe(),
        distroRepository.observeAll().map { all -> all.filter { it.state == DistroState.READY } }
    ) { state, hosts, keys, distros -> state.copy(hosts = hosts, keys = keys, distros = distros) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    fun saveHost(host: SshHost) {
        viewModelScope.launch {
            val result =
                if (host.id == 0L) hostRepository.add(host) else hostRepository.update(host)
            if (result is Outcome.Failure) report(SshMessage.HostFailed(result.error))
        }
    }

    fun deleteHost(id: Long) {
        viewModelScope.launch { hostRepository.remove(id) }
    }

    /** Opens a tab running `ssh` for the host; [onOpened] runs once the tab exists. */
    fun connect(hostId: Long, onOpened: () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, message = null) }
            when (val plan = connector.prepare(hostId)) {
                is SshResult.Failure -> report(SshMessage.Failed(plan.error))

                is SshResult.Success -> {
                    sessions.newSession(plan.value.distro.id, launchFactory.create(plan.value))
                    local.update { it.copy(busy = false) }
                    onOpened()
                }
            }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun report(message: SshMessage) =
        local.update { it.copy(busy = false, message = message) }
}
