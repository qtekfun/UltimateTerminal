// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.broadcast

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.map
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.session.SessionId

/** To which panes of a tab what is typed in one of them is also sent (Terminator's "broadcast"). */
sealed interface BroadcastMode {
    /** Only the pane that has the keyboard. */
    data object Off : BroadcastMode

    /** Every pane of the tab. */
    data object AllPanes : BroadcastMode

    /** The panes assigned to the group called [name]. */
    data class Group(val name: String) : BroadcastMode
}

/** What is being sent: characters, or a key that means something to the program. */
enum class InputKind {
    /** Typed or pasted characters. */
    TEXT,

    /** Ctrl and Alt chords, Escape, arrows and the like: they interrupt and drive programs. */
    CONTROL
}

/**
 * The broadcast state of one tab. Immutable, and held in memory only: a broadcast that survived a
 * restart would type into servers by surprise. The guard rails are the point of this class:
 *
 * - with a single pane there is nothing to broadcast to;
 * - with [textOnly] (the default) control keys go to the active pane alone, so a Ctrl+C or an
 *   arrow key meant for one server does not reach the others;
 * - a pane outside the group never leaks into it, and a pane that is not in the tab gets nothing.
 */
data class BroadcastState(
    val mode: BroadcastMode = BroadcastMode.Off,
    val textOnly: Boolean = true,
    private val groupOf: Map<SessionId, String> = emptyMap()
) {
    /** The panes that receive [kind] typed in [active], in the order of [panes]; never empty. */
    fun targets(active: SessionId, panes: List<SessionId>, kind: InputKind): List<SessionId> {
        val alone = listOf(active)
        return when {
            panes.size <= 1 || active !in panes -> alone

            kind == InputKind.CONTROL && textOnly -> alone

            else -> when (mode) {
                BroadcastMode.Off -> alone
                BroadcastMode.AllPanes -> panes
                is BroadcastMode.Group -> inGroup(mode.name, active, panes) ?: alone
            }
        }
    }

    /** True if typing in one pane of [panes] would reach another: the UI shows this clearly. */
    fun isEmitting(panes: List<SessionId>): Boolean = when {
        panes.size <= 1 -> false
        mode == BroadcastMode.Off -> false
        mode is BroadcastMode.Group -> panes.count { groupOf[it] == mode.name } > 1
        else -> true
    }

    fun withMode(mode: BroadcastMode) = copy(mode = mode)

    fun withTextOnly(textOnly: Boolean) = copy(textOnly = textOnly)

    /** The shortcut: off becomes "all panes", anything else becomes off. */
    fun toggled(): BroadcastState =
        copy(mode = if (mode == BroadcastMode.Off) BroadcastMode.AllPanes else BroadcastMode.Off)

    /** Puts [pane] in the group [group] (a name like any other), or out of every group if null. */
    fun assign(pane: SessionId, group: String?): Outcome<BroadcastState> = if (group == null) {
        Outcome.Success(copy(groupOf = groupOf - pane))
    } else {
        Validation.name(group).map { name -> copy(groupOf = groupOf + (pane to name)) }
    }

    /** The group [pane] belongs to, if any. */
    fun groupOf(pane: SessionId): String? = groupOf[pane]

    /** The names of the groups that have at least one pane. */
    fun groups(): Set<String> = groupOf.values.toSet()

    /** Forgets the panes that are gone, so a closed pane's number can never match a new one. */
    fun pruned(live: Collection<SessionId>): BroadcastState {
        val kept = groupOf.filterKeys { it in live }
        // A group left without panes cannot be the mode: broadcasting to nobody is "off".
        val groupGone = mode is BroadcastMode.Group && kept.values.none { it == mode.name }
        return copy(
            mode = if (groupGone) BroadcastMode.Off else mode,
            groupOf = kept
        )
    }

    private fun inGroup(name: String, active: SessionId, panes: List<SessionId>): List<SessionId>? =
        if (groupOf[active] == name) panes.filter { groupOf[it] == name } else null
}
