// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.profile.PaneOpening
import com.qtekfun.ultimateterminal.domain.profile.PlannedNode
import com.qtekfun.ultimateterminal.domain.profile.openings
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

/**
 * A [SessionFactory] that can also run a given command (proot running `ssh`, say) instead of
 * Android's own shell. Kept separate so the plain factory stays a single-method interface.
 */
interface LaunchingSessionFactory : SessionFactory {
    fun start(
        id: SessionId,
        layout: TerminalLayout,
        launch: SessionLaunch?,
        onExit: (Int) -> Unit
    ): SessionHandle?

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle? = start(id, layout, null, onExit)
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

    /** Ends a whole tab: its panes first, then its own session. */
    fun closeTab(id: SessionId) {
        state.value.tabCloseOrder(id).forEach(::close)
    }
}

/** What the pane logic needs from the session owner: it also starts and sizes the shells. */
interface PaneEditor : SessionEditor {
    /**
     * Splits the pane that has the keyboard; the new shell gets it. Null if nothing is active. An
     * [opening] (a profile) says where and how the new pane starts; without one it is a plain
     * shell in the same distro.
     */
    fun splitActive(orientation: SplitOrientation, opening: PaneOpening? = null): SessionId?

    /**
     * A new tab with the panes of [root], each started as its spec says; the first is the tab and
     * has the keyboard. Returns the tab's session.
     */
    fun openTab(root: PlannedNode): SessionId

    /** What the pane [id] was opened with, null for a plain one (and for one that is gone). */
    fun openingOf(id: SessionId): PaneOpening?

    /** Tells each pty the size of its pane, skipping those whose size did not change. */
    fun applyPaneLayouts(layouts: Map<SessionId, TerminalLayout>)
}

/**
 * Owns the session lifecycle: which sessions exist, which one is active, and whether the foreground
 * service has to run. It holds no Android types. Call it from one thread (the main thread): the
 * emulator library delivers its callbacks there.
 */
// The one owner of the sessions: the interfaces it implements are the operations of the screen.
@Suppress("TooManyFunctions")
class SessionController(
    private val factory: SessionFactory,
    private val service: ServiceControl,
    initialLayout: TerminalLayout = DEFAULT_LAYOUT
) : PaneEditor {
    private val handles = mutableMapOf<SessionId, SessionHandle>()
    private val openings = mutableMapOf<SessionId, PaneOpening>()
    private val sizes = PtySizes(handles)
    private val mutableState = MutableStateFlow(Sessions())
    private var serviceWanted = false

    /** The size the next session starts at: the last one the screen reported. */
    var layout: TerminalLayout = initialLayout
        private set

    override val state: StateFlow<Sessions> = mutableState.asStateFlow()

    override fun newSession(distroId: Long?): SessionId = newSession(distroId, null)

    /** Like [newSession], running [launch] instead of the Android shell when it is not null. */
    fun newSession(distroId: Long?, launch: SessionLaunch?): SessionId {
        val (next, id) = mutableState.value.created(distroId)
        // Published first: a shell that ends at once reports to a session that exists.
        publish(next)
        return startShell(id, launch)
    }

    override fun splitActive(orientation: SplitOrientation, opening: PaneOpening?): SessionId? {
        val source = mutableState.value.activeId
        // A plain split of a pane opened with a profile keeps its distro and user (D-FIX-8).
        val inherited = opening ?: source?.let { openings[it] }?.forSplit()
        val (next, id) = mutableState.value.split(orientation, inherited) ?: return null
        inherited?.let { openings[id] = it }
        publish(next)
        return startShell(id, null)
    }

    override fun openTab(root: PlannedNode): SessionId {
        val (next, ids) = mutableState.value.openedTab(root)
        // Remembered before any shell starts: the factory reads it when it begins.
        ids.zip(root.openings()).forEach { (id, opening) -> openings[id] = opening }
        publish(next)
        ids.forEach { startShell(it, null) }
        return ids.first()
    }

    override fun openingOf(id: SessionId): PaneOpening? = openings[id]

    private fun startShell(id: SessionId, launch: SessionLaunch?): SessionId {
        val onExit = { status: Int -> onExited(id, status) }
        // One start only: a launch that fails must not fall back to a second, plain shell.
        val handle = if (factory is LaunchingSessionFactory) {
            factory.start(id, layout, launch, onExit)
        } else {
            factory.start(id, layout, onExit)
        }
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
        sizes.forget(id)
        openings.remove(id)
        handles.remove(id)?.stop()
    }

    /** Ends and forgets every session, which also stops the service. */
    fun closeAll() {
        val stopped = handles.values.toList()
        handles.clear()
        openings.clear()
        sizes.clear()
        publish(mutableState.value.allClosed())
        stopped.forEach(SessionHandle::stop)
    }

    override fun edit(change: Sessions.() -> Sessions) {
        val before = mutableState.value
        val next = before.change()
        if (next == before) return
        publish(next)
        // A shell that comes to the front may have been left at the size of an older layout. The
        // panes of a split tab are sized one by one (applyPaneLayouts), not to the whole area.
        if (next.activeId != before.activeId) next.activeId?.let { sizes.whole(next, it, layout) }
    }

    /** The visible area changed: remember it for new sessions and resize the one on screen. */
    fun onLayout(newLayout: TerminalLayout) {
        layout = newLayout
        mutableState.value.let { state ->
            state.activeId?.let { sizes.whole(state, it, newLayout) }
        }
    }

    override fun applyPaneLayouts(layouts: Map<SessionId, TerminalLayout>) {
        layouts.forEach { (id, size) -> sizes.tell(id, size) }
    }

    private val onExited: (SessionId, Int) -> Unit = { id, status ->
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

/** Makes [id] the active session. */
fun SessionEditor.activate(id: SessionId) = edit { activated(id) }

/** Remembers the size each pty was last told, so a size that did not change is not sent again. */
private class PtySizes(private val handles: Map<SessionId, SessionHandle>) {
    private val applied = mutableMapOf<SessionId, TerminalLayout>()

    fun tell(id: SessionId, size: TerminalLayout) {
        if (applied[id] != size) {
            applied[id] = size
            handles[id]?.resize(size)
        }
    }

    /** Sizes [id] to the whole area, unless its tab is split: then its pane has its own size. */
    fun whole(state: Sessions, id: SessionId, size: TerminalLayout) {
        if (state.paneIdsOf(state.tabOf(id)).size == 1) {
            applied[id] = size
            handles[id]?.resize(size)
        }
    }

    fun forget(id: SessionId) {
        applied.remove(id)
    }

    fun clear() = applied.clear()
}
