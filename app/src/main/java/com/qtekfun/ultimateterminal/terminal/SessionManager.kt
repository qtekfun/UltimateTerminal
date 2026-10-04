// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import com.qtekfun.ultimateterminal.domain.session.PaneEditor
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.Sessions
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The one place that owns the terminal sessions. It lives as long as the process, not as long as an
 * activity, so a shell survives the activity being recreated or closed while the foreground service
 * keeps the process alive. Use it from the main thread only.
 */
@Singleton
class SessionManager @Inject constructor(@ApplicationContext context: Context) {
    private val factory = AndroidSessionFactory(context)
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

    fun newSession(distroId: Long? = null): SessionId = controller.newSession(distroId)

    fun close(id: SessionId) = controller.close(id)

    fun closeAll() = controller.closeAll()

    fun onLayout(layout: TerminalLayout) = controller.onLayout(layout)

    /** Replaces the active session with a fresh shell (the "restart" of a session that ended). */
    fun restartActive() {
        val ended = controller.state.value.active ?: return
        controller.close(ended.id)
        controller.newSession(ended.distroId)
    }
}
