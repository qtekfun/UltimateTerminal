// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.broadcast

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.map
import com.qtekfun.ultimateterminal.domain.session.SessionEditor
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.Sessions
import com.qtekfun.ultimateterminal.domain.session.paneIdsOf
import com.qtekfun.ultimateterminal.domain.session.tabOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/** What the screen needs to know about broadcasting in the active tab. */
data class BroadcastView(
    val mode: BroadcastMode = BroadcastMode.Off,
    /** True if typing in one pane reaches another: the indicator is shown while this holds. */
    val emitting: Boolean = false,
    /** The panes that receive typed text (see [InputKind.TEXT]); only the active one when not emitting. */
    val targets: Set<SessionId> = emptySet(),
    /** The group the pane that has the keyboard belongs to. */
    val focusedGroup: String? = null,
    val groups: Set<String> = emptySet()
)

/**
 * The broadcast of each tab (SPEC RF-12): one [BroadcastState] per tab, in memory only (D-T12b-5).
 * It decides where typed input goes ([targets]) and what the screen shows ([views]); the rules are
 * in [BroadcastState]. A tab that is left with a single pane forgets its broadcast, so splitting
 * it again later does not start typing into the new pane without the user asking.
 */
class BroadcastController(private val editor: SessionEditor) {
    private val states = MutableStateFlow<Map<SessionId, BroadcastState>>(emptyMap())

    /** The broadcast of the active tab, again whenever it or the panes change. */
    val views: Flow<BroadcastView> = combine(editor.state, states, ::viewOf).distinctUntilChanged()

    /** The panes that receive [kind] typed now in the pane that has the keyboard; never more than needed. */
    fun targets(kind: InputKind): List<SessionId> {
        val sessions = editor.state.value
        val active = sessions.activeId ?: return emptyList()
        val panes = sessions.paneIdsOf(sessions.tabOf(active))
        return stateOf(sessions, states.value).targets(active, panes, kind)
    }

    /** Turns the broadcast of the active tab on (all its panes) or off. Nothing to do in a lone pane. */
    fun toggle() = change { _, state -> state.toggled() }

    /** Sends to the group of the pane that has the keyboard, if it has one. */
    fun sendToFocusedGroup() = change { focused, state ->
        state.groupOf(focused)?.let { state.withMode(BroadcastMode.Group(it)) } ?: state
    }

    /** Stops the broadcast of the active tab. */
    fun stop() = change { _, state -> state.withMode(BroadcastMode.Off) }

    /** Puts the pane that has the keyboard in group [group] (null: in none). */
    fun assignFocused(group: String?): Outcome<Unit> {
        val sessions = editor.state.value
        val active = sessions.activeId ?: return Outcome.Success(Unit)
        val tab = sessions.tabOf(active)
        return stateOf(sessions, states.value).assign(active, group).map { assigned ->
            states.update { it + (tab to assigned) }
        }
    }

    private fun change(transform: (SessionId, BroadcastState) -> BroadcastState) {
        val sessions = editor.state.value
        val active = sessions.activeId ?: return
        val tab = sessions.tabOf(active)
        if (sessions.paneIdsOf(tab).size < 2) return
        states.update { all ->
            val next = transform(active, stateOf(sessions, all))
            // Tabs that are gone take their broadcast with them.
            all.filterKeys { key -> sessions.items.any { it.id == key } } + (tab to next)
        }
    }

    /** The state of the active tab as it is now: panes that are gone are forgotten, a lone pane has none. */
    private fun stateOf(sessions: Sessions, all: Map<SessionId, BroadcastState>): BroadcastState {
        val active = sessions.activeId ?: return BroadcastState()
        val tab = sessions.tabOf(active)
        val panes = sessions.paneIdsOf(tab)
        return if (panes.size <
            2
        ) {
            BroadcastState()
        } else {
            (all[tab] ?: BroadcastState()).pruned(panes)
        }
    }

    private fun viewOf(sessions: Sessions, all: Map<SessionId, BroadcastState>): BroadcastView {
        val active = sessions.activeId ?: return BroadcastView()
        val panes = sessions.paneIdsOf(sessions.tabOf(active))
        val state = stateOf(sessions, all)
        return BroadcastView(
            mode = state.mode,
            emitting = state.isEmitting(panes),
            targets = state.targets(active, panes, InputKind.TEXT).toSet(),
            focusedGroup = state.groupOf(active),
            groups = state.groups()
        )
    }
}
