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

data class SessionInfo(val id: SessionId, val state: SessionState)

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
    fun created(): Pair<Sessions, SessionId> {
        val id = SessionId(nextId)
        val next = copy(
            items = items + SessionInfo(id, SessionState.Running),
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
}

/** Whether the partial wake lock must be held: only if the user asked for it and a shell runs. */
fun wakeLockWanted(keepAwake: Boolean, sessions: Sessions): Boolean =
    keepAwake && sessions.needsService
