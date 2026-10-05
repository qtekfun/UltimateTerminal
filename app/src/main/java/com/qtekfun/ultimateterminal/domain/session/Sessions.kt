// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

@JvmInline
value class SessionId(val value: Int)

sealed interface SessionState {
    /** The shell is running. */
    data object Running : SessionState

    /** The shell ended with [status]; the session stays listed so its output can still be read. */
    data class Exited(val status: Int) : SessionState
}

/**
 * One terminal session, which the user sees as a tab. [title] is what the user typed, or null to
 * show the default name; [distroId] is the distro the tab was opened in, null for the Android
 * shell. Opening a shell inside a distro arrives with T07.
 */
data class SessionInfo(
    val id: SessionId,
    val state: SessionState,
    val title: String? = null,
    val distroId: Long? = null,
    /** The profile the tab was opened with, which names it (D-FIX-2); null when none was used. */
    val profileName: String? = null
)

/**
 * The panes of a tab that has been split: [tree] (its leaves are the sessions), the pane that has
 * the keyboard ([focus]) and whether that pane is shown alone ([zoomed]). A tab that was never split
 * has no entry in [Sessions.panes]: it is one session.
 */
data class PaneTab(val tree: PaneNode, val focus: SessionId, val zoomed: Boolean = false)

/**
 * An immutable snapshot of the terminal sessions the app owns. Every change returns a new snapshot,
 * so the lifecycle rules live here and can be tested without a device.
 *
 * A tab is a session that is not a pane of another one: [tabs]. A split tab keeps its extra
 * sessions in [items] too, so the service and the notification still see every shell, but only the
 * tab's own session (the one that opened it) appears in the tab bar. [activeId] is the session that
 * has the keyboard, which in a split tab is its focused pane.
 */
data class Sessions(
    val items: List<SessionInfo> = emptyList(),
    val activeId: SessionId? = null,
    val nextId: Int = 1,
    val panes: Map<SessionId, PaneTab> = emptyMap()
) {
    /** What the tab bar lists, in order: every session that is not a pane of another one. */
    val tabs: List<SessionInfo> get() = items.filter { tabOf(it.id) == it.id }

    val active: SessionInfo? get() = items.firstOrNull { it.id == activeId }

    /** Sessions whose shell has not ended. */
    val runningCount: Int get() = items.count { it.state == SessionState.Running }

    /** The foreground service has to run while a shell does, and only then (SPEC RF-07). */
    val needsService: Boolean get() = runningCount > 0

    /** A new running session, which becomes the active one. */
    fun created(distroId: Long? = null): Pair<Sessions, SessionId> {
        val id = SessionId(nextId)
        val next = copy(
            items = items + SessionInfo(id, SessionState.Running, distroId = distroId),
            activeId = id,
            nextId = nextId + 1
        )
        return next to id
    }

    /** Marks [id] as ended. An unknown id, or one that already ended, changes nothing. */
    fun exited(id: SessionId, status: Int): Sessions {
        val current = items.firstOrNull { it.id == id }
        if (current == null || current.state != SessionState.Running) return this
        val ended = current.copy(state = SessionState.Exited(status))
        return copy(items = items.map { if (it.id == id) ended else it })
    }

    /**
     * Removes [id]. A pane of a split tab leaves its sibling the space (see [closedPane]); a tab's
     * last session takes the tab with it, and if it was the active one, a tab with a running shell
     * is preferred as the new one.
     */
    fun closed(id: SessionId): Sessions = when {
        items.none { it.id == id } -> this
        panes.values.any { id in it.tree } -> closedPane(id)
        else -> closedPlain(id)
    }

    /** Removes every session. Ids are not reused. */
    fun allClosed(): Sessions = copy(items = emptyList(), activeId = null)

    /**
     * Goes to the tab of [id], to the pane that had the keyboard there; an unknown id changes
     * nothing. To move the keyboard to a pane of the same tab use [focused].
     */
    fun activated(id: SessionId): Sessions =
        if (items.any { it.id == id }) copy(activeId = focusOf(tabOf(id))) else this

    /** Changes the active tab as [target] says; a target that does not exist changes nothing. */
    fun switched(target: TabSwitch): Sessions = when (target) {
        is TabSwitch.ById -> activated(target.id)
        is TabSwitch.Number -> tabs.getOrNull(target.number - 1)?.let { activated(it.id) } ?: this
        TabSwitch.Next -> stepped(1)
        TabSwitch.Previous -> stepped(-1)
    }

    /** The neighbouring tab, wrapping around at the ends; with no active tab, the first or last. */
    private fun stepped(step: Int): Sessions {
        val list = tabs
        if (list.size < 2) return this
        val current = list.indexOfFirst { it.id == activeId?.let(::tabOf) }
        val target = when {
            current >= 0 -> Math.floorMod(current + step, list.size)
            step > 0 -> 0
            else -> list.lastIndex
        }
        return activated(list[target].id)
    }

    /**
     * Gives [id] the name the user typed. A blank name goes back to the default one (see
     * [TabTitle]). An unknown id changes nothing.
     */
    fun renamed(id: SessionId, title: String?): Sessions {
        val normalized = TabTitle.normalize(title)
        val renamed = items.map { if (it.id == id) it.copy(title = normalized) else it }
        return if (renamed == items) this else copy(items = renamed)
    }

    /** Moves the tab [id] to position [toIndex] (0-based, among the tabs, kept inside the list). */
    fun moved(id: SessionId, toIndex: Int): Sessions {
        val list = tabs
        val from = list.indexOfFirst { it.id == id }
        val to = toIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (from < 0 || from == to) return this
        val reordered = list.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return copy(items = reordered + items.filter { tabOf(it.id) != it.id })
    }

    /**
     * What closing [id] has to do: a shell that still runs is not killed without asking. For a
     * tab, every one of its panes counts.
     */
    fun closeAction(id: SessionId): CloseAction {
        if (items.none { it.id == id }) return CloseAction.Ignore
        val group = if (tabOf(id) == id) paneIdsOf(id) else listOf(id)
        val running = items.any { it.id in group && it.state == SessionState.Running }
        return if (running) CloseAction.Confirm else CloseAction.Close
    }
}

/** The outcome of asking to close a tab. */
enum class CloseAction {
    /** There is no such tab. */
    Ignore,

    /** Its shell already ended, so nothing is lost. */
    Close,

    /** Its shell still runs: ask the user first. */
    Confirm
}

/** Where a change of active tab goes. */
sealed interface TabSwitch {
    data object Next : TabSwitch

    data object Previous : TabSwitch

    /** The tab in position [number], counting from 1. */
    data class Number(val number: Int) : TabSwitch

    data class ById(val id: SessionId) : TabSwitch
}

/** Whether the partial wake lock must be held: only if the user asked for it and a shell runs. */
fun wakeLockWanted(keepAwake: Boolean, sessions: Sessions): Boolean =
    keepAwake && sessions.needsService
