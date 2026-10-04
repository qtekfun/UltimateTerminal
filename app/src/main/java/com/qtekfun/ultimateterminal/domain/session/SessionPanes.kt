// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation

/**
 * Splits the pane that has the keyboard: a new running session, in the same distro, takes the second
 * half and gets the keyboard. A zoomed tab is shown whole again. Null when there is no active
 * session.
 */
fun Sessions.split(orientation: SplitOrientation): Pair<Sessions, SessionId>? {
    val source = activeId?.let { active -> items.firstOrNull { it.id == active } } ?: return null
    val tab = tabOf(source.id)
    val id = SessionId(nextId)
    val tree = treeOf(tab).split(source.id, orientation, id)
    val next = copy(
        items = items + SessionInfo(id, SessionState.Running, distroId = source.distroId),
        activeId = id,
        nextId = nextId + 1,
        panes = panes + (tab to PaneTab(tree, focus = id))
    )
    return next to id
}

/** Gives the keyboard to the pane [id], without leaving its tab; an unknown id changes nothing. */
fun Sessions.focused(id: SessionId): Sessions {
    if (items.none { it.id == id }) return this
    val tab = tabOf(id)
    val split = panes[tab]
    return copy(
        activeId = id,
        panes = if (split == null) panes else panes + (tab to split.copy(focus = id))
    )
}

/** The active tab and its panes; null when nothing is active or the tab was never split. */
private fun Sessions.activeSplit(): Pair<SessionId, PaneTab>? =
    activeId?.let(::tabOf)?.let { tab -> panes[tab]?.let { tab to it } }

/** Moves the divider at [path] of the active tab; a tab that is not split changes nothing. */
fun Sessions.ratioSet(path: DividerPath, ratio: Float): Sessions {
    val (tab, split) = activeSplit() ?: return this
    return copy(panes = panes + (tab to split.copy(tree = split.tree.withRatio(path, ratio))))
}

/** Exchanges the places of two panes of the active tab. */
fun Sessions.swappedPanes(a: SessionId, b: SessionId): Sessions {
    val (tab, split) = activeSplit() ?: return this
    val swapped = split.copy(tree = split.tree.swapped(a, b))
    return if (a in split.tree && b in split.tree) copy(panes = panes + (tab to swapped)) else this
}

/** Shows the pane that has the keyboard alone, or all of them again. */
fun Sessions.zoomToggled(): Sessions {
    val (tab, split) = activeSplit() ?: return this
    return copy(panes = panes + (tab to split.copy(zoomed = !split.zoomed)))
}

/**
 * Removes a pane of a split tab; its neighbour takes the space. If it was the tab's own session,
 * the next pane in reading order becomes the tab, in the same place and with the same name. The
 * keyboard goes to the pane before it, or the one after when it was the first.
 */
internal fun Sessions.closedPane(id: SessionId): Sessions {
    val tab = tabOf(id)
    val current = panes.getValue(tab)
    val order = current.tree.leaves()
    val remaining = current.tree.removed(id) ?: return this
    val left = remaining.leaves()
    val successor = left[(order.indexOf(id) - 1).coerceAtLeast(0)]
    val focus = if (current.focus == id) successor else current.focus
    val newTab = if (id == tab) left.first() else tab
    val newItems = if (id == tab) {
        val promoted = items.first { it.id == newTab }
        val title = items.first { it.id == id }.title
        items.mapNotNull {
            when (it.id) {
                id -> promoted.copy(title = title)
                newTab -> null
                else -> it
            }
        }
    } else {
        items.filterNot { it.id == id }
    }
    val newPanes = panes - tab +
        if (left.size >
            1
        ) {
            mapOf(newTab to current.copy(tree = remaining, focus = focus))
        } else {
            emptyMap()
        }
    return copy(
        items = newItems,
        activeId = if (activeId == id) focus else activeId,
        panes = newPanes
    )
}
