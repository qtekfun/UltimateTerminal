// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Closing one pane of a split tab. A shell that still runs is not killed without asking, so the
 * request waits in [pending] for the user's answer. A tab that is not split has no pane to close
 * (closing the tab is the tab bar's job), so nothing happens.
 */
class PaneCloseConfirmation(private val editor: PaneEditor, scope: CoroutineScope) {
    private val asked = MutableStateFlow<SessionId?>(null)

    /** The pane waiting for the user to confirm closing it, or null. One that vanished is dropped. */
    val pending: StateFlow<SessionId?> = combine(asked, editor.state) { id, sessions ->
        id?.takeIf { wanted -> sessions.items.any { it.id == wanted } }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Closes the pane that has the keyboard, or asks first when its shell still runs. */
    fun request() {
        val sessions = editor.state.value
        val id = sessions.activeId ?: return
        if (sessions.paneIdsOf(sessions.tabOf(id)).size < 2) return
        when (sessions.closeAction(id)) {
            CloseAction.Ignore -> Unit
            CloseAction.Close -> editor.close(id)
            CloseAction.Confirm -> asked.value = id
        }
    }

    fun confirm() {
        val id = asked.value ?: return
        asked.value = null
        editor.close(id)
    }

    fun dismiss() {
        asked.value = null
    }
}
