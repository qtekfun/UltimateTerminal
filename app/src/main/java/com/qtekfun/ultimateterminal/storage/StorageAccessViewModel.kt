// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageAccess
import com.qtekfun.ultimateterminal.domain.storage.StorageToggle
import com.qtekfun.ultimateterminal.domain.storage.StorageToggleAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StorageAccessUiState(
    val enabled: Boolean = false,
    val permissionGranted: Boolean = false
)

/** The switch for the shared storage (SPEC RF-05). The decisions are in [StorageToggle]. */
@HiltViewModel
class StorageAccessViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val access: SharedStorageAccess
) : ViewModel() {
    private val permission = MutableStateFlow(access.isPermissionGranted())

    val uiState: StateFlow<StorageAccessUiState> = combine(
        settings.observe().map { it.sharedStorage },
        permission
    ) { enabled, granted -> StorageAccessUiState(enabled, granted) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            StorageAccessUiState()
        )

    /** The permission can change in the system settings while the app is in the background. */
    fun refreshPermission() {
        permission.value = access.isPermissionGranted()
    }

    /** Returns true if the caller must show the permission dialog now. */
    fun onToggled(turnOn: Boolean): Boolean {
        refreshPermission()
        return when (StorageToggle.onToggled(turnOn, permission.value)) {
            StorageToggleAction.REQUEST_PERMISSION -> true
            StorageToggleAction.ENABLE -> store(true).let { false }
            StorageToggleAction.DISABLE -> store(false).let { false }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        refreshPermission()
        store(StorageToggle.onPermissionResult(granted) == StorageToggleAction.ENABLE)
    }

    private fun store(enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(sharedStorage = enabled) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
