// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.Distro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the keyboard shortcuts can ask of the tabs. */
interface TabCommands {
    fun newTab()

    /** Closes the active tab, asking first if its shell still runs. */
    fun requestCloseActive()

    fun switchTo(target: TabSwitch)
}

/**
 * The tab bar: what it shows and what its buttons and shortcuts do. Each tab is one session, so
 * switching never interrupts the shells of the others. Closing a tab whose shell still runs waits
 * for the user's answer in [closeConfirmation].
 */
class TabsController(
    private val editor: SessionEditor,
    distros: Flow<List<Distro>>,
    scope: CoroutineScope
) : TabCommands {
    private val installed: StateFlow<List<Distro>> =
        distros.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val pendingClose = MutableStateFlow<SessionId?>(null)

    val tabs: StateFlow<List<TabItem>> = combine(editor.state, installed, ::tabItems)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Where a new tab can open, for the long press on the "new tab" button. */
    val distroChoices: StateFlow<List<DistroOption>> = installed
        .map(::distroOptions)
        .stateIn(scope, SharingStarted.Eagerly, distroOptions(emptyList()))

    /** The tab waiting for the user to confirm closing it, or null. One that vanished is dropped. */
    val closeConfirmation: StateFlow<SessionId?> =
        combine(pendingClose, editor.state) { id, sessions ->
            id?.takeIf { wanted -> sessions.items.any { it.id == wanted } }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun newTab() = newTabIn(defaultDistroId(installed.value))

    /** Opens a tab in [distroId], or in the Android shell when it is null. */
    fun newTabIn(distroId: Long?) {
        editor.newSession(distroId)
    }

    /** Closes [id], or asks first when its shell still runs. */
    fun requestClose(id: SessionId) {
        when (editor.state.value.closeAction(id)) {
            CloseAction.Ignore -> Unit
            CloseAction.Close -> editor.close(id)
            CloseAction.Confirm -> pendingClose.value = id
        }
    }

    override fun requestCloseActive() {
        editor.state.value.activeId?.let(::requestClose)
    }

    /** The user said yes: close the tab that was waiting. */
    fun confirmClose() {
        val id = pendingClose.value ?: return
        pendingClose.value = null
        editor.close(id)
    }

    fun dismissClose() {
        pendingClose.value = null
    }

    fun rename(id: SessionId, title: String?) = editor.edit { renamed(id, title) }

    fun move(id: SessionId, toIndex: Int) = editor.edit { moved(id, toIndex) }

    override fun switchTo(target: TabSwitch) = editor.edit { switched(target) }
}
