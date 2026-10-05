// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.profile.LayoutSaver
import com.qtekfun.ultimateterminal.domain.profile.PaneOpener
import com.qtekfun.ultimateterminal.domain.profile.RestoreResult
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * The saved layouts: list, save the panes of the active tab, open and delete. The rules are in the
 * domain ([LayoutSaver], [PaneOpener]); this only connects them to the screen.
 */
@HiltViewModel
class LayoutsViewModel @Inject constructor(
    private val repository: LayoutRepository,
    private val saver: LayoutSaver,
    private val opener: PaneOpener
) : ViewModel() {
    /** Null until the stored layouts arrive. */
    val layouts: StateFlow<List<Layout>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Saves the active tab under [name]; with [replace], over the layout that has that name. */
    fun save(name: String, replace: Boolean, onResult: (Outcome<Layout>) -> Unit) {
        viewModelScope.launch { onResult(saver.save(name, replace)) }
    }

    /** Opens [layout] as a new tab and tells [onResult] what it changed. */
    fun open(layout: Layout, onResult: (RestoreResult) -> Unit) {
        viewModelScope.launch { onResult(opener.restore(layout)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }
}
