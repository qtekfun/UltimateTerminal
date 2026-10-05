// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.support

import android.app.Instrumentation
import android.content.Context
import com.qtekfun.ultimateterminal.data.proot.LaunchPlan
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.terminal.ShellStart
import com.qtekfun.ultimateterminal.terminal.TerminalSessionHost

/**
 * A real pty with the app's own [TerminalSessionHost] on it, running the shell that the planner
 * chose (proot and a distro). The emulator callbacks need the main thread, so everything that
 * touches the host goes through `runOnMainSync`, as the service does.
 */
internal class GuestTerminal(
    private val context: Context,
    private val instrumentation: Instrumentation
) : AutoCloseable {
    private var host: TerminalSessionHost? = null

    fun start(plan: LaunchPlan.InDistro, grid: GridSize) {
        val start = ShellStart.proot(
            plan.launch,
            context.filesDir.absolutePath,
            context.cacheDir.absolutePath,
            System.getenv()
        )
        onMain {
            val created = TerminalSessionHost(context)
            host = created
            created.resize(grid, CELL_WIDTH_PX, CELL_HEIGHT_PX)
            created.launch(start, plan.notice)
        }
    }

    /** Types [text] and presses Enter. */
    fun type(text: String) = onMain { host?.write(text + "\n") }

    /** Tells the pty a new window size, which also reaches the guest as SIGWINCH. */
    fun resize(grid: GridSize) = onMain { host?.resize(grid, CELL_WIDTH_PX, CELL_HEIGHT_PX) }

    /** Every line of the screen and its history, trimmed. */
    fun lines(): List<String> {
        var text = ""
        onMain { text = host?.emulator?.screen?.transcriptText.orEmpty() }
        return text.lines().map { it.trim() }
    }

    /** Waits until some line satisfies [matches]; false if none does within [timeoutMs]. */
    fun awaitLine(timeoutMs: Long = DEFAULT_WAIT_MS, matches: (String) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (lines().any(matches)) return true
            Thread.sleep(POLL_MS)
        }
        return lines().any(matches)
    }

    override fun close() = onMain { host?.stop() }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private companion object {
        const val CELL_WIDTH_PX = 10
        const val CELL_HEIGHT_PX = 20
        const val DEFAULT_WAIT_MS = 60_000L
        const val POLL_MS = 250L
    }
}
