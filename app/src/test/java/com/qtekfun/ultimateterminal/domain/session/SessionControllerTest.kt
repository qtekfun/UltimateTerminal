// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.profile.PaneLook
import com.qtekfun.ultimateterminal.domain.profile.PaneOpening
import com.qtekfun.ultimateterminal.domain.profile.PaneSpec
import com.qtekfun.ultimateterminal.domain.profile.PaneTarget
import com.qtekfun.ultimateterminal.domain.profile.PlannedNode
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeHandle : SessionHandle {
    val resizes = mutableListOf<TerminalLayout>()
    var stopped = 0

    override fun resize(layout: TerminalLayout) {
        resizes += layout
    }

    override fun stop() {
        stopped++
    }
}

private class FakeFactory : SessionFactory {
    val handles = mutableMapOf<SessionId, FakeHandle>()
    val startLayouts = mutableListOf<TerminalLayout>()
    private val exits = mutableMapOf<SessionId, (Int) -> Unit>()
    var failToStart = false
    var exitImmediately: Int? = null

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle? {
        startLayouts += layout
        if (failToStart) return null
        exitImmediately?.let(onExit)
        exits[id] = onExit
        return FakeHandle().also { handles[id] = it }
    }

    fun shellEnds(id: SessionId, status: Int) = exits.getValue(id)(status)
}

private class FakeService : ServiceControl {
    val calls = mutableListOf<Boolean>()

    override fun setRunning(wanted: Boolean) {
        calls += wanted
    }
}

class SessionControllerTest {
    private val factory = FakeFactory()
    private val service = FakeService()
    private val controller = SessionController(factory, service)
    private lateinit var racing: SessionController
    private val big = TerminalLayout(GridSize(200, 50), 9, 18)

    @Test
    fun theFirstSessionStartsTheServiceExactlyOnce() {
        controller.newSession()
        controller.newSession()

        assertEquals(listOf(true), service.calls)
    }

    @Test
    fun theServiceStopsWhenTheLastShellEnds() {
        val first = controller.newSession()
        val second = controller.newSession()

        factory.shellEnds(first, 0)
        assertEquals(listOf(true), service.calls)

        factory.shellEnds(second, 130)
        assertEquals(listOf(true, false), service.calls)
        assertEquals(SessionState.Exited(130), controller.state.value.items.last().state)
    }

    @Test
    fun anEndedSessionIsKeptUntilItIsClosed() {
        val id = controller.newSession()
        factory.shellEnds(id, 1)

        assertEquals(1, controller.state.value.items.size)
        assertEquals(0, factory.handles.getValue(id).stopped)

        controller.close(id)

        assertTrue(controller.state.value.items.isEmpty())
        assertEquals(1, factory.handles.getValue(id).stopped)
    }

    @Test
    fun closingARunningSessionEndsItsShellAndStopsTheService() {
        val id = controller.newSession()

        controller.close(id)

        assertEquals(1, factory.handles.getValue(id).stopped)
        assertEquals(listOf(true, false), service.calls)
    }

    @Test
    fun closingAnUnknownSessionDoesNothing() {
        controller.newSession()

        controller.close(SessionId(99))

        assertEquals(1, controller.state.value.items.size)
        assertEquals(listOf(true), service.calls)
    }

    @Test
    fun closeAllEndsEveryShellAndStopsTheService() {
        val first = controller.newSession()
        val second = controller.newSession()

        controller.closeAll()

        assertTrue(controller.state.value.items.isEmpty())
        assertEquals(1, factory.handles.getValue(first).stopped)
        assertEquals(1, factory.handles.getValue(second).stopped)
        assertEquals(listOf(true, false), service.calls)
    }

    @Test
    fun closeAllWithNoSessionsStillWorks() {
        controller.closeAll()

        assertTrue(service.calls.isEmpty())
    }

    @Test
    fun aShellThatCannotStartIsReportedAsFailedAndNeverStartsTheService() {
        factory.failToStart = true

        val id = controller.newSession()

        assertEquals(
            SessionState.Exited(SessionController.START_FAILED),
            controller.state.value.items.single { it.id == id }.state
        )
        // The service was asked to start for the new session, then to stop as it had already failed.
        assertEquals(listOf(true, false), service.calls)
    }

    @Test
    fun aShellThatEndsBeforeStartReturnsIsStillRecorded() {
        factory.exitImmediately = 2

        val id = controller.newSession()

        assertEquals(
            SessionState.Exited(2),
            controller.state.value.items.single {
                it.id == id
            }.state
        )
        assertFalse(controller.state.value.needsService)
    }

    @Test
    fun theLayoutReachesTheActiveSessionAndNewOnes() {
        val first = controller.newSession()

        controller.onLayout(big)
        val second = controller.newSession()

        assertEquals(listOf(big), factory.handles.getValue(first).resizes)
        assertEquals(big, factory.startLayouts.last())
        assertEquals(emptyList<TerminalLayout>(), factory.handles.getValue(second).resizes)
        assertEquals(big, controller.layout)
    }

    @Test
    fun aSessionStartsAtTheDefaultSizeUntilTheScreenReportsOne() {
        controller.newSession()

        assertEquals(SessionController.DEFAULT_LAYOUT, factory.startLayouts.single())
    }

    @Test
    fun aLayoutWithNoSessionIsJustRemembered() {
        controller.onLayout(big)

        assertEquals(big, controller.layout)
        assertTrue(factory.handles.isEmpty())
    }

    @Test
    fun activatingASessionResizesItToTheCurrentLayout() {
        val first = controller.newSession()
        val second = controller.newSession()
        // `second` is the active one, so only it follows the layout change.
        controller.onLayout(big)
        assertEquals(emptyList<TerminalLayout>(), factory.handles.getValue(first).resizes)

        controller.activate(first)

        assertEquals(first, controller.state.value.activeId)
        assertEquals(listOf(big), factory.handles.getValue(first).resizes)
        assertEquals(listOf(big), factory.handles.getValue(second).resizes)
    }

    @Test
    fun activatingTheActiveOrAnUnknownSessionDoesNothing() {
        val id = controller.newSession()
        controller.onLayout(big)
        val before = factory.handles.getValue(id).resizes.size

        controller.activate(id)
        controller.activate(SessionId(50))

        assertEquals(before, factory.handles.getValue(id).resizes.size)
    }

    @Test
    fun aSessionClosedWhileItWasStartingIsStopped() {
        val handle = FakeHandle()
        racing = SessionController(
            { _, _, _ ->
                // Something closes everything while the shell is being created.
                racing.closeAll()
                handle
            },
            service
        )

        racing.newSession()

        assertEquals(1, handle.stopped)
        assertTrue(racing.state.value.items.isEmpty())
    }

    @Test
    fun aSessionRemembersTheDistroItWasOpenedIn() {
        val id = controller.newSession(distroId = 4L)

        assertEquals(4L, controller.state.value.items.single { it.id == id }.distroId)
    }

    @Test
    fun theDistroIsPublishedBeforeTheShellStartsSoTheFactoryCanReadIt() {
        var seen: Long? = null
        lateinit var probing: SessionController
        probing = SessionController(
            { id, _, _ ->
                seen = probing.state.value.items.firstOrNull { it.id == id }?.distroId
                FakeHandle()
            },
            service
        )

        probing.newSession(distroId = 9L)

        assertEquals(9L, seen)
    }

    @Test
    fun aSplitOpensInTheSameDistro() {
        controller.newSession(distroId = 4L)

        val split = checkNotNull(controller.splitActive(SplitOrientation.HORIZONTAL))

        assertEquals(4L, controller.state.value.items.single { it.id == split }.distroId)
    }
}

class SessionControllerOpeningsTest {
    private val started = mutableListOf<SessionId>()
    private val stopped = mutableListOf<SessionId>()
    private val controller = SessionController(
        SessionFactory { id, _, _ ->
            started += id
            object : SessionHandle {
                override fun resize(layout: TerminalLayout) = Unit

                override fun stop() {
                    stopped += id
                }
            }
        },
        { }
    )
    private val opening = PaneOpening(
        PaneSpec(PaneTarget.AndroidShell, PaneLook(scrollbackLines = 500), "ls\r")
    )
    private val tab = PlannedNode.Split(
        SplitOrientation.VERTICAL,
        0.5f,
        PlannedNode.Pane(opening.spec),
        PlannedNode.Pane(opening.spec)
    )

    @Test
    fun openingATabStartsAShellForEveryPaneAndRemembersWhatEachWasOpenedWith() {
        val first = controller.openTab(tab)

        val ids = controller.state.value.paneIdsOf(first)
        assertEquals(ids, started)
        assertEquals(listOf(opening, opening), ids.map { controller.openingOf(it) })
    }

    @Test
    fun aPaneSplitWithAnOpeningRemembersItAndAPlainSplitDoesNot() {
        controller.newSession()

        val profiled = controller.splitActive(SplitOrientation.VERTICAL, opening)!!
        val plain = controller.splitActive(SplitOrientation.VERTICAL)!!

        assertEquals(opening, controller.openingOf(profiled))
        assertEquals(null, controller.openingOf(plain))
    }

    @Test
    fun whatAPaneWasOpenedWithIsForgottenWhenItClosesOrEverythingCloses() {
        val first = controller.openTab(tab)
        val second = controller.state.value.paneIdsOf(first)[1]

        controller.close(second)
        assertEquals(null, controller.openingOf(second))
        assertEquals(opening, controller.openingOf(first))

        controller.closeAll()
        assertEquals(null, controller.openingOf(first))
        assertEquals(listOf(second, first), stopped)
    }
}
