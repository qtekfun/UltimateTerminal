// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation

/**
 * The panes of one tab as a binary tree whose leaves are terminal sessions. Immutable: every change
 * returns a new tree, so the rules live here and are tested without a device.
 *
 * [SplitOrientation] names the line that divides: [SplitOrientation.HORIZONTAL] is a horizontal
 * line, so `first` is above and `second` below; [SplitOrientation.VERTICAL] is a vertical line, so
 * `first` is on the left and `second` on the right.
 */
sealed interface PaneNode {
    /** One terminal. */
    data class Leaf(val id: SessionId) : PaneNode

    /** [ratio] is the share of the space that [first] takes, strictly between 0 and 1. */
    data class Branch(
        val orientation: SplitOrientation,
        val ratio: Float,
        val first: PaneNode,
        val second: PaneNode
    ) : PaneNode {
        init {
            require(ratio > 0f && ratio < 1f) { "ratio must be between 0 and 1: $ratio" }
        }
    }
}

/** Where a divider is in the tree: 0 goes into `first`, 1 into `second`; empty is the root. */
@JvmInline
value class DividerPath(val steps: List<Int>) {
    init {
        require(steps.all { it == 0 || it == 1 }) { "a step is 0 or 1: $steps" }
    }

    fun child(step: Int) = DividerPath(steps + step)
}

/** The sessions of the tree, in reading order (first to second, depth first). */
fun PaneNode.leaves(): List<SessionId> = when (this) {
    is PaneNode.Leaf -> listOf(id)
    is PaneNode.Branch -> first.leaves() + second.leaves()
}

operator fun PaneNode.contains(id: SessionId): Boolean = id in leaves()

/**
 * Splits the pane of [target] in two: it keeps its place and the new pane [newId] goes after it.
 * An unknown [target] changes nothing.
 */
fun PaneNode.split(
    target: SessionId,
    orientation: SplitOrientation,
    newId: SessionId,
    ratio: Float = EVEN_SPLIT
): PaneNode = when (this) {
    is PaneNode.Leaf ->
        if (id == target) {
            PaneNode.Branch(orientation, ratio, this, PaneNode.Leaf(newId))
        } else {
            this
        }

    is PaneNode.Branch -> copy(
        first = first.split(target, orientation, newId, ratio),
        second = second.split(target, orientation, newId, ratio)
    )
}

/**
 * The tree without the pane of [id]: its sibling takes the space it had. Null when it was the only
 * pane. An unknown [id] changes nothing.
 */
fun PaneNode.removed(id: SessionId): PaneNode? = when (this) {
    is PaneNode.Leaf -> if (this.id == id) null else this

    is PaneNode.Branch -> {
        val kept = listOfNotNull(first.removed(id), second.removed(id))
        when {
            kept.size == 2 -> copy(first = kept[0], second = kept[1])
            kept.size == 1 && id in this -> kept[0]
            else -> this
        }
    }
}

/** Gives the divider at [path] the share [ratio]; a path that leads nowhere changes nothing. */
fun PaneNode.withRatio(path: DividerPath, ratio: Float): PaneNode = when {
    this !is PaneNode.Branch -> this
    path.steps.isEmpty() -> copy(ratio = ratio)
    path.steps.first() == 0 -> copy(first = first.withRatio(DividerPath(path.steps.drop(1)), ratio))
    else -> copy(second = second.withRatio(DividerPath(path.steps.drop(1)), ratio))
}

/** Exchanges the places of two panes; the sessions themselves do not move. */
fun PaneNode.swapped(a: SessionId, b: SessionId): PaneNode = when (this) {
    is PaneNode.Leaf -> when (id) {
        a -> PaneNode.Leaf(b)
        b -> PaneNode.Leaf(a)
        else -> this
    }

    is PaneNode.Branch -> copy(first = first.swapped(a, b), second = second.swapped(a, b))
}

/** The stored form (T05, SPEC RF-12): the shape and the proportions, without the sessions. */
fun PaneNode.toLayoutNode(): LayoutNode = when (this) {
    is PaneNode.Leaf -> LayoutNode.Pane()

    is PaneNode.Branch ->
        LayoutNode.Split(orientation, ratio, first.toLayoutNode(), second.toLayoutNode())
}

/** A tree of the shape of [node], each pane taking its session from [sessionFor] in order. */
fun paneNodeOf(node: LayoutNode, sessionFor: () -> SessionId): PaneNode = when (node) {
    is LayoutNode.Pane -> PaneNode.Leaf(sessionFor())

    is LayoutNode.Split -> {
        // Evaluated in order: the first pane gets the first session.
        val first = paneNodeOf(node.first, sessionFor)
        PaneNode.Branch(node.orientation, node.ratio, first, paneNodeOf(node.second, sessionFor))
    }
}

/**
 * [ratio] kept at least [minShare] away from each end, and snapped to an even split when it is
 * within [SNAP] of it, so dragging a divider finds the middle by itself.
 */
fun snapRatio(ratio: Float, minShare: Float): Float {
    val limit = minShare.coerceIn(MIN_SHARE_FLOOR, EVEN_SPLIT)
    val clamped = ratio.coerceIn(limit, 1f - limit)
    return if (kotlin.math.abs(clamped - EVEN_SPLIT) <= SNAP) EVEN_SPLIT else clamped
}

const val EVEN_SPLIT = 0.5f
private const val SNAP = 0.03f
private const val MIN_SHARE_FLOOR = 0.02f
