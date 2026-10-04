// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import com.qtekfun.ultimateterminal.data.proot.ProotSessionPlanner
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.session.PaneEditor
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.SessionLaunch
import com.qtekfun.ultimateterminal.domain.session.Sessions
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one place that owns the terminal sessions. It lives as long as the process, not as long as an
 * activity, so a shell survives the activity being recreated or closed while the foreground service
 * keeps the process alive. Use it from the main thread only.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context,
    planner: ProotSessionPlanner,
    private val distros: DistroRepository
) {
    // The emulator library delivers its callbacks on the main thread, so everything starts there.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val factory = AndroidSessionFactory(context, planner, { distroOf(it) }, scope)
    private val controller = SessionController(factory, ServiceLauncher(context))

    val state: StateFlow<Sessions> get() = controller.state

    /** What the tab bar edits the sessions through. */
    val editor: PaneEditor get() = controller

    /** The host of the active session, emitted again when the active session changes. */
    val activeHost: Flow<TerminalSessionHost?> =
        controller.state.map { factory.host(it.activeId) }.distinctUntilChanged()

    fun currentHost(): TerminalSessionHost? = factory.host(controller.state.value.activeId)

    /** The host of any session, which a pane of a split tab draws. */
    fun hostOf(id: SessionId): TerminalSessionHost? = factory.host(id)

    /** The distro a session was opened in, read from the state published before its shell starts. */
    private val distroOf: (SessionId) -> Long? = { id ->
        controller.state.value.items.firstOrNull { it.id == id }?.distroId
    }

    /**
     * Opens a tab in the default distro if it is ready, else in Android's shell: what the app does
     * when it starts and when the notification asks for a new session.
     */
    fun newDefaultSession() {
        scope.launch {
            val ready = distros.getDefault()?.takeIf { it.state == DistroState.READY }
            controller.newSession(ready?.id)
        }
    }

    fun newSession(distroId: Long? = null): SessionId = controller.newSession(distroId)

    /** Opens a tab that runs [launch] (for example `ssh` in a distro) instead of the Android shell. */
    fun newSession(distroId: Long?, launch: SessionLaunch): SessionId =
        controller.newSession(distroId, launch)

    fun close(id: SessionId) = controller.close(id)

    fun closeAll() = controller.closeAll()

    fun onLayout(layout: TerminalLayout) = controller.onLayout(layout)

    /** Colors of every running shell, and of those started later. */
    fun applyScheme(scheme: TerminalColorScheme) = factory.applyScheme(scheme)

    /**
     * Replaces the active session with a fresh shell (the "restart" of a session that ended), in the
     * same distro. A tab that ran a launch (an `ssh` connection) is not run again: its key file was
     * removed when it ended, so reconnecting goes through the hosts screen. It opens a plain shell of
     * the distro, and if the distro is no longer usable the tab says so (D-T08b-9).
     */
    fun restartActive() {
        val ended = controller.state.value.active ?: return
        controller.close(ended.id)
        controller.newSession(ended.distroId)
    }
}
