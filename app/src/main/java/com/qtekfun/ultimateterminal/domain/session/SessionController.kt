// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the controller needs from a running shell. */
interface SessionHandle {
    /** Tells the pty the new size; it also reaches the program as `SIGWINCH`. */
    fun resize(layout: TerminalLayout)

    /** Ends the shell if it still runs and releases it. */
    fun stop()
}

/** Starts shells. The Android implementation owns the pty. */
fun interface SessionFactory {
    /**
     * Starts a shell of the given size and calls [onExit] with its status when it ends, which may
     * happen before this returns. Returns null if it could not be started.
     */
    fun start(id: SessionId, layout: TerminalLayout, onExit: (Int) -> Unit): SessionHandle?
}

/** Starts and stops the foreground service that keeps the shells alive. */
fun interface ServiceControl {
    /** Called only when the answer changes, so an implementation need not be idempotent. */
    fun setRunning(wanted: Boolean)
}

/** What the tab logic needs from the session owner, so it can be tested without a device. */
interface SessionEditor {
    val state: StateFlow<Sessions>

    /** Starts a shell, which becomes the active session, and records the distro it belongs to. */
    fun newSession(distroId: Long? = null): SessionId

    /** Ends and forgets one session. */
    fun close(id: SessionId)

    /** Applies a change to the sessions (rename, reorder, switch); the shells are not touched. */
    fun edit(change: Sessions.() -> Sessions)
}

/**
 * Owns the session lifecycle: which sessions exist, which one is active, and whether the foreground
 * service has to run. It holds no Android types. Call it from one thread (the main thread): the
 * emulator library delivers its callbacks there.
 */
class SessionController(
    private val factory: SessionFactory,
    private val service: ServiceControl,
    initialLayout: TerminalLayout = DEFAULT_LAYOUT
) : SessionEditor {
    private val handles = mutableMapOf<SessionId, SessionHandle>()
    private val mutableState = MutableStateFlow(Sessions())
    private var serviceWanted = false

    /** The size the next session starts at: the last one the screen reported. */
    var layout: TerminalLayout = initialLayout
        private set

    override val state: StateFlow<Sessions> = mutableState.asStateFlow()

    override fun newSession(distroId: Long?): SessionId {
        val (next, id) = mutableState.value.created(distroId)
        // Published first: a shell that ends at once reports to a session that exists.
        publish(next)
        val handle = factory.start(id, layout) { status -> onExited(id, status) }
        if (handle == null) {
            onExited(id, START_FAILED)
        } else if (mutableState.value.items.any { it.id == id }) {
            handles[id] = handle
        } else {
            // Closed while it was starting.
            handle.stop()
        }
        return id
    }

    override fun close(id: SessionId) {
        publish(mutableState.value.closed(id))
        handles.remove(id)?.stop()
    }

    /** Ends and forgets every session, which also stops the service. */
    fun closeAll() {
        val stopped = handles.values.toList()
        handles.clear()
        publish(mutableState.value.allClosed())
        stopped.forEach(SessionHandle::stop)
    }

    fun activate(id: SessionId) = edit { activated(id) }

    override fun edit(change: Sessions.() -> Sessions) {
        val before = mutableState.value
        val next = before.change()
        if (next == before) return
        publish(next)
        // A shell that comes to the front may have been left at the size of an older layout.
        if (next.activeId != before.activeId) next.activeId?.let { handles[it]?.resize(layout) }
    }

    /** The visible area changed: remember it for new sessions and resize the one on screen. */
    fun onLayout(newLayout: TerminalLayout) {
        layout = newLayout
        mutableState.value.activeId?.let { handles[it]?.resize(newLayout) }
    }

    private fun onExited(id: SessionId, status: Int) {
        publish(mutableState.value.exited(id, status))
    }

    private fun publish(next: Sessions) {
        mutableState.value = next
        if (next.needsService != serviceWanted) {
            serviceWanted = next.needsService
            service.setRunning(serviceWanted)
        }
    }

    companion object {
        /** Status shown for a shell that could not even start. */
        const val START_FAILED = -1

        /** Used until the screen reports its size, e.g. a session started from the notification. */
        val DEFAULT_LAYOUT =
            TerminalLayout(GridSize(columns = 80, rows = 24), cellWidthPx = 10, cellHeightPx = 20)
    }
}
