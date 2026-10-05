// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.distro.DistroManager
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.setup.FirstRunSetup
import com.qtekfun.ultimateterminal.domain.setup.SetupGate
import com.qtekfun.ultimateterminal.domain.setup.SetupInputs
import com.qtekfun.ultimateterminal.terminal.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Decides whether the first-run setup is shown. The install itself is not here: the screen drives
 * the shared [com.qtekfun.ultimateterminal.distro.DistroViewModel].
 *
 * "Skip" is kept in memory only, so the setup comes back on the next launch while there is no
 * distro.
 */
@HiltViewModel
class SetupViewModel internal constructor(
    distros: Flow<List<Distro>>,
    hasSessions: Flow<Boolean>,
    private val makeDefault: suspend (Long) -> Boolean
) : ViewModel() {
    @Inject
    constructor(manager: DistroManager, sessions: SessionManager) : this(
        manager.observe(),
        sessions.state.map { it.items.isNotEmpty() },
        { id -> manager.setDefault(id) is Outcome.Success }
    )

    private val gateState = MutableStateFlow(SetupGate.UNDECIDED)
    private val skipped = MutableStateFlow(false)

    val gate: StateFlow<SetupGate> = gateState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(distros, hasSessions, skipped) { list, open, skip ->
                SetupInputs(loaded = true, distros = list, hasSessions = open, skipped = skip)
            }.collect { inputs ->
                val pending = FirstRunSetup.distroToMakeDefault(inputs.distros)
                if (gateState.value == SetupGate.SHOWING && pending != null) {
                    // The new distro must be the default before the terminal opens its first tab;
                    // the change comes back as the next emission, which closes the gate (if it failed, close now).
                    if (!makeDefault(pending.id)) advance(inputs)
                } else {
                    advance(inputs)
                }
            }
        }
    }

    private fun advance(inputs: SetupInputs) = gateState.update { FirstRunSetup.next(it, inputs) }

    /** "Skip, use the Android shell for now". */
    fun skip() {
        skipped.value = true
    }
}
