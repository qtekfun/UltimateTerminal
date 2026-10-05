// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import android.os.SystemClock
import com.qtekfun.ultimateterminal.data.proot.LaunchPlan
import com.qtekfun.ultimateterminal.data.proot.ProotLaunch
import com.qtekfun.ultimateterminal.data.proot.ProotSessionPlanner
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem
import com.qtekfun.ultimateterminal.domain.profile.PaneOpening
import com.qtekfun.ultimateterminal.domain.profile.PaneTarget
import com.qtekfun.ultimateterminal.domain.profile.StartupInputGate
import com.qtekfun.ultimateterminal.domain.session.HostRegistry
import com.qtekfun.ultimateterminal.domain.session.LaunchingSessionFactory
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionHandle
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.SessionLaunch
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Starts a [TerminalSessionHost] per session and keeps them by id so the screen can find the one it
 * shows. What each one runs has a single source:
 *
 *  - a [SessionLaunch] given by the caller (the SSH hosts screen passes proot running `ssh`), used
 *    as it is; or
 *  - none, and then the [planner] decides from the distro the tab was opened in: proot with that
 *    distro, or Android's shell. A pane opened with a profile also brings its user, its scrollback
 *    and the command to type once the shell runs (D-T12b-2).
 *
 * The plan arrives a moment after the host exists, so [start] returns at once and the shell begins
 * when the plan and the screen's size are both known. What a launch put on disk (a key file) goes
 * away once, whichever way the session ends. It needs a real pty, so it is exercised on a device,
 * not in unit tests. [context] must be the application context: the hosts outlive any activity.
 * [scope] must run on the main thread: the emulator library delivers its callbacks there.
 */
class AndroidSessionFactory(
    private val context: Context,
    private val planner: ProotSessionPlanner,
    private val distroOf: (SessionId) -> Long?,
    /** What a pane was opened with (a profile, T12b), read when its shell begins. */
    private val openingOf: (SessionId) -> PaneOpening?,
    private val scope: CoroutineScope,
    private val scrollbackLines: suspend () -> Int
) : LaunchingSessionFactory {
    /**
     * The hosts of the running sessions. Observable: the sessions state names a tab before its host
     * exists, so whoever looks a host up must be told when it appears (see [HostRegistry]).
     */
    val hosts = HostRegistry<TerminalSessionHost>()
    private var scheme: TerminalColorScheme? = null

    fun host(id: SessionId?): TerminalSessionHost? = hosts[id]

    /** Makes [newScheme] the colors of every running shell, and of any shell started later. */
    fun applyScheme(newScheme: TerminalColorScheme) {
        scheme = newScheme
        hosts.values.forEach { it.applyScheme(newScheme) }
    }

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        launch: SessionLaunch?,
        onExit: (Int) -> Unit
    ): SessionHandle {
        val closed = AtomicBoolean(false)
        val cleanup: () -> Unit = {
            if (closed.compareAndSet(false, true)) launch?.onClosed?.invoke()
        }
        val host = TerminalSessionHost(context) { status ->
            cleanup()
            onExit(status)
        }
        scheme?.let(host::applyScheme)
        hosts.put(id, host)
        host.resize(layout.grid, layout.cellWidthPx, layout.cellHeightPx)
        val job = scope.launch { begin(host, id, launch, onExit) }
        return Handle(id, host, job, cleanup)
    }

    // The planner reads the database and the disk, and the pty or the fork can fail in ways the
    // library reports as plain runtime exceptions; either way the tab shows a message and ends as a
    // failed session instead of crashing the service.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun begin(
        host: TerminalSessionHost,
        id: SessionId,
        launch: SessionLaunch?,
        onExit: (Int) -> Unit
    ) {
        try {
            val opening = openingOf(id)
            host.transcriptRows = opening?.spec?.look?.scrollbackLines ?: scrollbackLines()
            val home = context.filesDir.absolutePath
            val tmp = context.cacheDir.absolutePath
            val inherited = System.getenv()
            if (launch != null) {
                val start = ShellStart.proot(
                    ProotLaunch(launch.command, launch.environment),
                    home,
                    tmp,
                    inherited
                )
                host.launch(start)
            } else {
                val user = (opening?.spec?.target as? PaneTarget.InDistro)?.user
                val plan = planner.plan(distroOf(id), user)
                when (plan) {
                    LaunchPlan.AndroidShell ->
                        host.launch(ShellStart.androidShell(home, tmp, inherited))

                    is LaunchPlan.FallbackToAndroid ->
                        host.launch(ShellStart.androidShell(home, tmp, inherited), plan.notice)

                    is LaunchPlan.InDistro ->
                        host.launch(
                            ShellStart.proot(plan.launch, home, tmp, inherited),
                            plan.notice
                        )

                    is LaunchPlan.Failed -> failed(host, plan.problem, onExit)
                }
                if (plan !is LaunchPlan.Failed) typeStartupInput(host, opening)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            host.stop()
            failed(host, LaunchProblem.Unexpected, onExit)
        }
    }

    /**
     * Types the command of a profile (with its Enter) into the shell once it has printed its first
     * prompt and gone quiet, or after a maximum wait ([StartupInputGate], D-FIX-5). Typed earlier the
     * terminal would echo it twice, once before the prompt exists and once after.
     */
    private suspend fun typeStartupInput(host: TerminalSessionHost, opening: PaneOpening?) {
        val input = opening?.spec?.startupInput ?: return
        val gate = StartupInputGate(SystemClock.elapsedRealtime())
        var seen = host.outputCount
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (host.outputCount != seen) {
                seen = host.outputCount
                gate.onOutput(now)
            }
            if (gate.isReady(now)) break
            delay(STARTUP_POLL_MILLIS)
        }
        host.write(input)
    }

    private fun failed(host: TerminalSessionHost, problem: LaunchProblem, onExit: (Int) -> Unit) {
        host.fail(problem)
        onExit(SessionController.START_FAILED)
    }

    private inner class Handle(
        private val id: SessionId,
        private val host: TerminalSessionHost,
        private val job: Job,
        private val cleanup: () -> Unit
    ) : SessionHandle {
        override fun resize(layout: TerminalLayout) {
            host.resize(layout.grid, layout.cellWidthPx, layout.cellHeightPx)
        }

        override fun stop() {
            job.cancel()
            host.stop()
            hosts.remove(id)
            cleanup()
        }
    }
}

/** How often the start-up command checks whether the shell went quiet. */
private const val STARTUP_POLL_MILLIS = 50L
