// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class LaunchHandle : SessionHandle {
    var stops = 0

    override fun resize(layout: TerminalLayout) = Unit

    override fun stop() {
        stops++
    }
}

private class RecordingLaunchFactory(private val fail: Boolean = false) : LaunchingSessionFactory {
    val launches = mutableListOf<SessionLaunch?>()
    val handles = mutableListOf<LaunchHandle>()

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        launch: SessionLaunch?,
        onExit: (Int) -> Unit
    ): SessionHandle? {
        launches += launch
        return if (fail) null else LaunchHandle().also { handles += it }
    }
}

private class PlainOnlyFactory : SessionFactory {
    var starts = 0

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle {
        starts++
        return LaunchHandle()
    }
}

private class IdleService : ServiceControl {
    override fun setRunning(wanted: Boolean) = Unit
}

class SessionLaunchTest {
    private val launch =
        SessionLaunch(listOf("proot", "-r", "/x", "ssh", "u@h"), mapOf("PROOT_TMP_DIR" to "/t"))

    @Test
    fun aLaunchReachesAFactoryThatCanRunIt() {
        val factory = RecordingLaunchFactory()
        SessionController(factory, IdleService()).newSession(7L, launch)
        assertEquals(1, factory.launches.size)
        assertSame(launch, factory.launches.single())
    }

    @Test
    fun aPlainNewSessionStartsWithoutALaunch() {
        val factory = RecordingLaunchFactory()
        SessionController(factory, IdleService()).newSession(null)
        assertNull(factory.launches.single())
    }

    @Test
    fun aFactoryThatCannotRunACommandStillStartsASession() {
        val factory = PlainOnlyFactory()
        val controller = SessionController(factory, IdleService())
        controller.newSession(null, launch)
        assertEquals(1, factory.starts)
    }

    @Test
    fun aLaunchThatFailsToStartDoesNotFallBackToAPlainShell() {
        val factory = RecordingLaunchFactory(fail = true)
        val controller = SessionController(factory, IdleService())
        val id = controller.newSession(null, launch)
        assertEquals(1, factory.launches.size)
        assertTrue(controller.state.value.items.single { it.id == id }.state is SessionState.Exited)
    }

    @Test
    fun closingASessionStopsItsHandleOnce() {
        val factory = RecordingLaunchFactory()
        val controller = SessionController(factory, IdleService())
        val id = controller.newSession(null, launch)
        controller.close(id)
        assertEquals(1, factory.handles.single().stops)
    }

    @Test
    fun theLaunchKeepsItsCommandOutOfToString() {
        assertFalse(launch.toString().contains("ssh"))
        assertEquals("SessionLaunch(5 arguments)", launch.toString())
    }

    @Test
    fun theLaunchCallsItsCleanupOnlyWhenAskedTo() {
        var cleaned = 0
        val withCleanup = SessionLaunch(listOf("x"), emptyMap()) { cleaned++ }
        assertEquals(0, cleaned)
        withCleanup.onClosed()
        assertEquals(1, cleaned)
    }
}
