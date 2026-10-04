// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import com.qtekfun.ultimateterminal.domain.session.SessionFactory
import com.qtekfun.ultimateterminal.domain.session.SessionHandle
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout

/**
 * Starts a [TerminalSessionHost] per session and keeps them by id so the screen can find the one it
 * shows. It needs a real pty, so it is exercised on a device, not in unit tests. [context] must be
 * the application context: the hosts outlive any activity.
 */
class AndroidSessionFactory(private val context: Context) : SessionFactory {
    private val hosts = mutableMapOf<SessionId, TerminalSessionHost>()

    fun host(id: SessionId?): TerminalSessionHost? = id?.let(hosts::get)

    // The pty or the fork can fail in ways the library reports as plain runtime exceptions; a shell
    // that cannot start is reported as a failed session instead of crashing the service.
    @Suppress("TooGenericExceptionCaught")
    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle? {
        val host = TerminalSessionHost(context, onExit)
        return try {
            host.resize(layout.grid, layout.cellWidthPx, layout.cellHeightPx)
            hosts[id] = host
            Handle(id, host)
        } catch (_: RuntimeException) {
            host.stop()
            null
        }
    }

    private inner class Handle(private val id: SessionId, private val host: TerminalSessionHost) :
        SessionHandle {
        override fun resize(layout: TerminalLayout) {
            host.resize(layout.grid, layout.cellWidthPx, layout.cellHeightPx)
        }

        override fun stop() {
            host.stop()
            hosts.remove(id)
        }
    }
}
