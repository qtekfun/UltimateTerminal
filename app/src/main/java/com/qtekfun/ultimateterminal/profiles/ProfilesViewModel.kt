// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.profile.OpenAs
import com.qtekfun.ultimateterminal.domain.profile.OpenResult
import com.qtekfun.ultimateterminal.domain.profile.PaneOpener
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the profiles screen lists: the profiles, and the distros a profile can choose among. */
data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    /** Only distros that are ready: a profile can name another, but the form offers these. */
    val distros: List<Distro> = emptyList(),
    /** False until the stored profiles arrive, so the screen does not flash "no profiles". */
    val loaded: Boolean = false
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * The profiles screen: list, create, edit, delete and open in a tab or a split. The rules are in
 * the domain ([com.qtekfun.ultimateterminal.domain.profile.ProfileForm], [PaneOpener]); this only
 * connects them to the repositories.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository,
    distros: DistroRepository,
    private val opener: PaneOpener
) : ViewModel() {
    val uiState: StateFlow<ProfilesUiState> = combine(
        repository.observeAll(),
        distros.observeAll().map { all -> all.filter { it.state == DistroState.READY } }
    ) { profiles, ready -> ProfilesUiState(profiles, ready, loaded = true) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ProfilesUiState()
        )

    /** Saves [profile] (a new one has id 0) and tells [onResult] how it went. */
    fun save(profile: Profile, onResult: (Outcome<*>) -> Unit) {
        viewModelScope.launch {
            onResult(if (profile.id == 0L) repository.add(profile) else repository.update(profile))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }

    /** Opens [profile] as [how] and tells [onResult] what happened. */
    fun open(profile: Profile, how: OpenAs, onResult: (OpenResult) -> Unit) {
        viewModelScope.launch { onResult(opener.open(profile, how)) }
    }
}
