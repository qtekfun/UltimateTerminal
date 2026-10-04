// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.session.PaneNode
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.leaves

/** What a pane running now must remember to be restored: its profile and its start-up command. */
data class PaneDescription(val profileId: Long? = null, val command: String? = null)

/** Saving the panes of a tab as a named layout (SPEC RF-12). */
object LayoutSaving {
    const val MAX_LAYOUTS = 100

    /**
     * A [Layout] called [name] with the shape of [tree], each pane described by [describe]. [existing]
     * are the layouts already saved: names are unique ignoring case, except the layout being
     * replaced ([replacing], an id), so saving over one by its own name works. A start-up command
     * that is not one plain line is refused here, instead of being stored to fail later.
     */
    fun build(
        name: String,
        tree: PaneNode,
        describe: (SessionId) -> PaneDescription,
        existing: List<Layout>,
        replacing: Long = 0L
    ): Outcome<Layout> = Validation.name(name).flatMap { clean ->
        val others = existing.filter { it.id != replacing || replacing == 0L }
        when {
            others.any { it.name.equals(clean, ignoreCase = true) } ->
                Outcome.Failure(DomainError.NameTaken(clean))

            replacing == 0L && existing.size >= MAX_LAYOUTS ->
                Outcome.Failure(DomainError.InvalidValue("layouts"))

            hasBadCommand(tree, describe) -> Outcome.Failure(DomainError.InvalidValue("command"))

            else -> Outcome.Success(Layout(replacing, clean, toNode(tree, describe)))
        }
    }

    /** The stored shape of [tree], each leaf carrying what [describe] says (blank command: none). */
    fun toNode(tree: PaneNode, describe: (SessionId) -> PaneDescription): LayoutNode = when (tree) {
        is PaneNode.Leaf -> {
            val pane = describe(tree.id)
            LayoutNode.Pane(pane.profileId, pane.command?.trim()?.ifEmpty { null })
        }

        is PaneNode.Branch -> LayoutNode.Split(
            tree.orientation,
            tree.ratio,
            toNode(tree.first, describe),
            toNode(tree.second, describe)
        )
    }

    private fun hasBadCommand(tree: PaneNode, describe: (SessionId) -> PaneDescription): Boolean =
        tree.leaves().any {
            StartupCommand.check(describe(it).command) is StartupCommand.Check.Invalid
        }
}
