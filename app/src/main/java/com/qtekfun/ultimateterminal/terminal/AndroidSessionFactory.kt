// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import com.qtekfun.ultimateterminal.data.proot.LaunchPlan
import com.qtekfun.ultimateterminal.data.proot.ProotSessionPlanner
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem
import com.qtekfun.ultimateterminal.domain.launch.ShellRequest
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionFactory
import com.qtekfun.ultimateterminal.domain.session.SessionHandle
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Starts a [TerminalSessionHost] per session and keeps them by id so the screen can find the one it
 * shows. What each one runs is decided by the [planner] from the session's [ShellRequest]
 * (a distro through proot, or Android's shell); the plan arrives a moment after the host exists, so
 * [start] returns at once and the shell begins when the plan and the screen's size are both known.
 * It needs a real pty, so it is exercised on a device, not in unit tests. [context] must be the
 * application context: the hosts outlive any activity. [scope] must run on the main thread: the
 * emulator library delivers its callbacks there.
 */
class AndroidSessionFactory(
    private val context: Context,
    private val planner: ProotSessionPlanner,
    private val requestOf: (SessionId) -> ShellRequest,
    private val scope: CoroutineScope
) : SessionFactory {
    private val hosts = mutableMapOf<SessionId, TerminalSessionHost>()
    private var scheme: TerminalColorScheme? = null

    fun host(id: SessionId?): TerminalSessionHost? = id?.let(hosts::get)

    /** Makes [newScheme] the colors of every running shell, and of any shell started later. */
    fun applyScheme(newScheme: TerminalColorScheme) {
        scheme = newScheme
        hosts.values.forEach { it.applyScheme(newScheme) }
    }

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle {
        val host = TerminalSessionHost(context, onExit)
        scheme?.let(host::applyScheme)
        hosts[id] = host
        host.resize(layout.grid, layout.cellWidthPx, layout.cellHeightPx)
        val job = scope.launch { begin(host, requestOf(id), onExit) }
        return Handle(id, host, job)
    }

    // The planner reads the database and the disk, and the pty or the fork can fail in ways the
    // library reports as plain runtime exceptions; either way the tab shows a message and ends as a
    // failed session instead of crashing the service.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun begin(
        host: TerminalSessionHost,
        request: ShellRequest,
        onExit: (Int) -> Unit
    ) {
        try {
            val home = context.filesDir.absolutePath
            val tmp = context.cacheDir.absolutePath
            val inherited = System.getenv()
            when (val plan = planner.plan(request)) {
                LaunchPlan.AndroidShell -> host.launch(
                    ShellStart.androidShell(home, tmp, inherited)
                )

                is LaunchPlan.FallbackToAndroid ->
                    host.launch(ShellStart.androidShell(home, tmp, inherited), plan.notice)

                is LaunchPlan.InDistro ->
                    host.launch(ShellStart.proot(plan.launch, home, tmp, inherited), plan.notice)

                is LaunchPlan.Failed -> failed(host, plan.problem, onExit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            host.stop()
            failed(host, LaunchProblem.Unexpected, onExit)
        }
    }

    private fun failed(host: TerminalSessionHost, problem: LaunchProblem, onExit: (Int) -> Unit) {
        host.fail(problem)
        onExit(SessionController.START_FAILED)
    }

    private inner class Handle(
        private val id: SessionId,
        private val host: TerminalSessionHost,
        private val job: Job
    ) : SessionHandle {
        override fun resize(layout: TerminalLayout) {
            host.resize(layout.grid, layout.cellWidthPx, layout.cellHeightPx)
        }

        override fun stop() {
            job.cancel()
            host.stop()
            hosts.remove(id)
        }
    }
}
