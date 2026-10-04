// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** The tab that owns [id]: itself unless it is a pane of a split tab. */
fun Sessions.tabOf(id: SessionId): SessionId =
    panes.entries.firstOrNull { id in it.value.tree }?.key ?: id

/** The panes of [tab]: just the session itself unless the tab was split. */
fun Sessions.treeOf(tab: SessionId): PaneNode = panes[tab]?.tree ?: PaneNode.Leaf(tab)

/** The pane of [tab] that has the keyboard. */
fun Sessions.focusOf(tab: SessionId): SessionId = panes[tab]?.focus ?: tab

/** The sessions of [tab], in reading order. */
fun Sessions.paneIdsOf(tab: SessionId): List<SessionId> = treeOf(tab).leaves()

/** The tree to draw for [tab]: the whole of it, or only the focused pane while it is zoomed. */
fun Sessions.visibleTree(tab: SessionId): PaneNode {
    val split = panes[tab] ?: return PaneNode.Leaf(tab)
    return if (split.zoomed) PaneNode.Leaf(split.focus) else split.tree
}

/** Whether any shell of [tab] still runs. */
fun Sessions.isTabRunning(tab: SessionId): Boolean =
    items.any { it.id in paneIdsOf(tab) && it.state == SessionState.Running }

/** The order in which to close the sessions of the tab [id]: the panes first, then the tab itself. */
fun Sessions.tabCloseOrder(id: SessionId): List<SessionId> =
    if (tabOf(id) != id) listOf(id) else paneIdsOf(id).filter { it != id } + id

/**
 * The session that gets the keyboard when the active tab went away: the keyboard pane of the last
 * tab with a running shell, else of the last tab; null if none is left.
 */
fun Sessions.fallbackTab(): SessionId? {
    val list = tabs
    val tab = list.lastOrNull { isTabRunning(it.id) } ?: list.lastOrNull()
    return tab?.let { focusOf(it.id) }
}

/** Removes a session that is a whole tab by itself; the keyboard goes to [fallbackTab] if it had it. */
internal fun Sessions.closedPlain(id: SessionId): Sessions {
    val without = copy(items = items.filterNot { it.id == id })
    return if (activeId != id) without else without.copy(activeId = without.fallbackTab())
}
