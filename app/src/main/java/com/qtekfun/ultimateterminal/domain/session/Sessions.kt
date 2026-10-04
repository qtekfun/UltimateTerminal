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
    val distroId: Long? = null
)

/**
 * An immutable snapshot of the terminal sessions the app owns. Every change returns a new snapshot,
 * so the lifecycle rules live here and can be tested without a device.
 */
data class Sessions(
    val items: List<SessionInfo> = emptyList(),
    val activeId: SessionId? = null,
    private val nextId: Int = 1
) {
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

    /** Removes [id]. If it was the active one, a running session is preferred as the new one. */
    fun closed(id: SessionId): Sessions {
        if (items.none { it.id == id }) return this
        val remaining = items.filterNot { it.id == id }
        val newActive = if (activeId != id) {
            activeId
        } else {
            val preferred = remaining.lastOrNull { it.state == SessionState.Running }
            (preferred ?: remaining.lastOrNull())?.id
        }
        return copy(items = remaining, activeId = newActive)
    }

    /** Removes every session. Ids are not reused. */
    fun allClosed(): Sessions = copy(items = emptyList(), activeId = null)

    /** Makes [id] the active session; an unknown id changes nothing. */
    fun activated(id: SessionId): Sessions =
        if (items.any { it.id == id }) copy(activeId = id) else this

    /** Changes the active tab as [target] says; a target that does not exist changes nothing. */
    fun switched(target: TabSwitch): Sessions = when (target) {
        is TabSwitch.ById -> activated(target.id)
        is TabSwitch.Number -> items.getOrNull(target.number - 1)?.let { activated(it.id) } ?: this
        TabSwitch.Next -> stepped(1)
        TabSwitch.Previous -> stepped(-1)
    }

    /** The neighbouring tab, wrapping around at the ends; with no active tab, the first or last. */
    private fun stepped(step: Int): Sessions {
        if (items.size < 2) return this
        val current = items.indexOfFirst { it.id == activeId }
        val target = when {
            current >= 0 -> Math.floorMod(current + step, items.size)
            step > 0 -> 0
            else -> items.lastIndex
        }
        return copy(activeId = items[target].id)
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

    /** Moves [id] to position [toIndex] (0-based, kept inside the list). */
    fun moved(id: SessionId, toIndex: Int): Sessions {
        val from = items.indexOfFirst { it.id == id }
        val to = toIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (from < 0 || from == to) return this
        val reordered = items.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return copy(items = reordered)
    }

    /** What closing [id] has to do: a shell that still runs is not killed without asking. */
    fun closeAction(id: SessionId): CloseAction {
        val session = items.firstOrNull { it.id == id } ?: return CloseAction.Ignore
        return if (session.state == SessionState.Running) CloseAction.Confirm else CloseAction.Close
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
