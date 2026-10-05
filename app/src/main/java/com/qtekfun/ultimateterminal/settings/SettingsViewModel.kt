// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.launch.ResolvConf
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.settings.ScrollbackChoices
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * What the settings screen reads and writes: the stored [AppSettings]. Every change is a
 * transformation of what is stored, not of what the screen last saw, so two quick taps never undo
 * each other. The rules (valid DNS servers, the scrollback range, the limits of the extra keys)
 * are in the domain; this only applies them.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) :
    ViewModel() {
    /** Null until the first read of the stored settings arrives. */
    val settings: StateFlow<AppSettings?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setKeepAwake(on: Boolean) = edit { it.copy(keepAwake = on) }

    fun setScrollback(lines: Int) =
        edit { it.copy(defaultScrollbackLines = ScrollbackChoices.forEmulator(lines)) }

    fun editExtraKeys(transform: (ExtraKeysConfig) -> ExtraKeysConfig) =
        edit { it.copy(extraKeys = transform(it.extraKeys)) }

    fun editShortcuts(transform: (ShortcutMap) -> ShortcutMap) =
        edit { it.copy(shortcuts = transform(it.shortcuts)) }

    fun resetShortcuts() = edit { it.copy(shortcuts = ShortcutMap.defaults()) }

    fun resetExtraKeys() = edit { it.copy(extraKeys = ExtraKeysConfig.default()) }

    /** An empty list means the built-in servers. */
    fun setDnsServers(servers: List<String>) = edit {
        it.copy(dnsFallbackServers = servers.ifEmpty { ResolvConf.FALLBACK_SERVERS })
    }

    private fun edit(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
