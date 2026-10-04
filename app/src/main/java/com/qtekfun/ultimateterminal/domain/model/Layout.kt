// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A saved arrangement of panes (SPEC RF-12). [id] is 0 for a layout that has not been saved. */
data class Layout(val id: Long = 0L, val name: String, val root: LayoutNode)

enum class SplitOrientation { HORIZONTAL, VERTICAL }

/** A node of the pane tree: either one terminal or a split of two sub-trees. */
@Serializable
sealed interface LayoutNode {
    /** One terminal, with the profile it uses (null = default) and the command it starts. */
    @Serializable
    @SerialName("pane")
    data class Pane(val profileId: Long? = null, val command: String? = null) : LayoutNode

    /** [ratio] is the share of the space that [first] takes, strictly between 0 and 1. */
    @Serializable
    @SerialName("split")
    data class Split(
        val orientation: SplitOrientation,
        val ratio: Float,
        val first: LayoutNode,
        val second: LayoutNode
    ) : LayoutNode {
        init {
            require(ratio > 0f && ratio < 1f) { "ratio must be between 0 and 1: $ratio" }
        }
    }
}

/** Number of terminals in the tree. */
fun LayoutNode.paneCount(): Int = when (this) {
    is LayoutNode.Pane -> 1
    is LayoutNode.Split -> first.paneCount() + second.paneCount()
}
