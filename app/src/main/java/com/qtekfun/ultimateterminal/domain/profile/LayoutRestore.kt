// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.snapRatio

/** A node of a restore plan: the shape of the saved layout, each pane with what it opens. */
sealed interface PlannedNode {
    data class Pane(val spec: PaneSpec) : PlannedNode

    data class Split(
        val orientation: SplitOrientation,
        val ratio: Float,
        val first: PlannedNode,
        val second: PlannedNode
    ) : PlannedNode
}

/** Something the restore changed, with the place it happened (counted in reading order from 0). */
sealed interface LayoutNotice {
    /** The pane at [paneIndex] opens with [notice]: a deleted profile, a gone font, a fallback. */
    data class ForPane(val paneIndex: Int, val notice: PaneNotice) : LayoutNotice

    /** The split at [splitIndex] was too lopsided for the panes to stay usable and was evened out. */
    data class RatioAdjusted(val splitIndex: Int) : LayoutNotice
}

/** What to open to bring a saved layout back. */
data class LayoutRestorePlan(
    val layoutName: String,
    val root: PlannedNode,
    val notices: List<LayoutNotice>
) {
    /** The panes in reading order (first to second, depth first), as the sessions will be created. */
    val panes: List<PaneSpec> get() = root.panes()
}

private fun PlannedNode.panes(): List<PaneSpec> = when (this) {
    is PlannedNode.Pane -> listOf(spec)
    is PlannedNode.Split -> first.panes() + second.panes()
}

/** Why a saved layout is not restored at all. */
enum class LayoutRefusal { TOO_MANY_PANES, TOO_DEEP }

sealed interface LayoutRestore {
    data class Ready(val plan: LayoutRestorePlan) : LayoutRestore

    data class Refused(val reason: LayoutRefusal) : LayoutRestore
}

/**
 * Works out what a saved [Layout] opens (SPEC RF-12). A layout is data from disk, a backup or
 * another device, so it is never trusted to be sane: its size is bounded, a pane whose profile or
 * distro is gone still opens (as the default profile, with a notice) and a split that would leave a
 * pane unusably thin is evened out. It never throws and starts nothing.
 */
class LayoutRestorePlanner(
    private val resolver: PaneSpecResolver,
    private val profiles: Map<Long, Profile>,
    /** The smallest share of a split that either side may have. */
    private val minShare: Float = DEFAULT_MIN_SHARE
) {
    fun plan(layout: Layout): LayoutRestore = when {
        !layout.root.depthWithin(MAX_DEPTH) -> LayoutRestore.Refused(LayoutRefusal.TOO_DEEP)

        layout.root.panes() > MAX_PANES -> LayoutRestore.Refused(LayoutRefusal.TOO_MANY_PANES)

        else -> {
            val walk = Walk()
            val root = walk.plan(layout.root)
            LayoutRestore.Ready(LayoutRestorePlan(layout.name, root, walk.notices))
        }
    }

    /** Numbers the panes and splits as it goes, so notices can say where. */
    private inner class Walk {
        val notices = mutableListOf<LayoutNotice>()
        private var panes = 0
        private var splits = 0

        fun plan(node: LayoutNode): PlannedNode = when (node) {
            is LayoutNode.Pane -> pane(node)

            is LayoutNode.Split -> {
                val splitIndex = splits++
                val ratio = snapRatio(node.ratio, minShare)
                if (node.ratio < minShare || node.ratio > 1f - minShare) {
                    notices += LayoutNotice.RatioAdjusted(splitIndex)
                }
                // In order: the first child gets the first panes.
                val first = plan(node.first)
                PlannedNode.Split(node.orientation, ratio, first, plan(node.second))
            }
        }

        private fun pane(node: LayoutNode.Pane): PlannedNode {
            val index = panes++
            val profileId = node.profileId
            val saved = profileId?.let(profiles::get)
            val ready = resolver.resolveOrDegrade(saved, node.command)
            if (profileId != null && saved == null) {
                notices += LayoutNotice.ForPane(index, PaneNotice.ProfileMissing(profileId))
            }
            ready.notices.forEach { notices += LayoutNotice.ForPane(index, it) }
            return PlannedNode.Pane(ready.spec)
        }
    }

    companion object {
        const val MAX_PANES = 16
        const val MAX_DEPTH = 8
        const val DEFAULT_MIN_SHARE = 0.1f
    }
}

private fun LayoutNode.panes(): Int = when (this) {
    is LayoutNode.Pane -> 1
    is LayoutNode.Split -> first.panes() + second.panes()
}

/** True if the tree is at most [limit] levels deep; stops looking at the limit, so it is bounded. */
private fun LayoutNode.depthWithin(limit: Int): Boolean = when (this) {
    is LayoutNode.Pane -> true

    is LayoutNode.Split ->
        limit > 0 && first.depthWithin(limit - 1) && second.depthWithin(limit - 1)
}
