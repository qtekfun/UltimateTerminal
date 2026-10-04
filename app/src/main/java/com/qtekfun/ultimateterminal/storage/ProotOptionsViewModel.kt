// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The compatibility switch: proot without its seccomp filter, the first thing to try if a distro fails. */
@HiltViewModel
class ProotOptionsViewModel @Inject constructor(private val settings: SettingsRepository) :
    ViewModel() {
    val compatibilityMode: StateFlow<Boolean> = settings.observe()
        .map { it.prootCompatibilityMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun setCompatibilityMode(enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(prootCompatibilityMode = enabled) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
