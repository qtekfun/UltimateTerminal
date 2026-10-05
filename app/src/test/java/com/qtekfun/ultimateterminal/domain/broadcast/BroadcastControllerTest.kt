// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.broadcast

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionFactory
import com.qtekfun.ultimateterminal.domain.session.SessionHandle
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.activate
import com.qtekfun.ultimateterminal.domain.session.focused
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BroadcastControllerTest {
    private val sessions = SessionController(
        SessionFactory { _, _, _ ->
            object : SessionHandle {
                override fun resize(layout: TerminalLayout) = Unit

                override fun stop() = Unit
            }
        },
        { }
    )
    private val broadcast = BroadcastController(sessions)
    private val first = sessions.newSession()
    private val second = sessions.splitActive(SplitOrientation.VERTICAL)!!

    private suspend fun view() = broadcast.views.first()

    private fun focus(id: SessionId) = sessions.edit { focused(id) }

    @Test
    fun nothingIsBroadcastUntilAskedFor() = runTest {
        assertEquals(BroadcastMode.Off, view().mode)
        assertFalse(view().emitting)
        assertEquals(listOf(second), broadcast.targets(InputKind.TEXT))
    }

    @Test
    fun toggleSendsTypedTextToEveryPaneButControlKeysOnlyToTheActiveOne() = runTest {
        broadcast.toggle()

        assertTrue(view().emitting)
        assertEquals(setOf(first, second), view().targets)
        assertEquals(listOf(first, second), broadcast.targets(InputKind.TEXT))
        assertEquals(listOf(second), broadcast.targets(InputKind.CONTROL))

        broadcast.toggle()

        assertFalse(view().emitting)
        assertEquals(listOf(second), broadcast.targets(InputKind.TEXT))
    }

    @Test
    fun aLonePaneCannotBroadcastAndRemembersNothingWhenSplitAgain() = runTest {
        val alone = SessionController(
            SessionFactory { _, _, _ ->
                object : SessionHandle {
                    override fun resize(layout: TerminalLayout) = Unit

                    override fun stop() = Unit
                }
            },
            { }
        )
        val lone = BroadcastController(alone)
        alone.newSession()

        lone.toggle()
        alone.splitActive(SplitOrientation.VERTICAL)

        assertFalse(lone.views.first().emitting)
        assertEquals(BroadcastMode.Off, lone.views.first().mode)
    }

    @Test
    fun closingTheOtherPaneEndsTheBroadcastForGood() = runTest {
        broadcast.toggle()
        sessions.close(first)
        assertFalse(view().emitting)

        sessions.splitActive(SplitOrientation.VERTICAL)

        assertFalse(view().emitting)
        assertEquals(BroadcastMode.Off, view().mode)
    }

    @Test
    fun noActiveSessionMeansNoTargetsAndNoView() = runTest {
        val empty = SessionController(
            SessionFactory { _, _, _ ->
                object : SessionHandle {
                    override fun resize(layout: TerminalLayout) = Unit

                    override fun stop() = Unit
                }
            },
            { }
        )
        val none = BroadcastController(empty)

        none.toggle()
        assertEquals(emptyList<SessionId>(), none.targets(InputKind.TEXT))
        assertEquals(BroadcastView(), none.views.first())
        assertEquals(Outcome.Success(Unit), none.assignFocused("a"))
    }

    @Test
    fun aGroupBroadcastsOnlyWhereThePaneHasThatGroup() = runTest {
        val third = sessions.splitActive(SplitOrientation.HORIZONTAL)!!
        // first and third are in group "web"; second is not.
        focus(first)
        assertEquals(Outcome.Success(Unit), broadcast.assignFocused("web"))
        focus(third)
        broadcast.assignFocused("web")

        broadcast.sendToFocusedGroup()

        assertEquals(BroadcastMode.Group("web"), view().mode)
        assertEquals("web", view().focusedGroup)
        assertEquals(setOf("web"), view().groups)
        assertEquals(setOf(first, third), view().targets)
        focus(second)
        assertEquals(listOf(second), broadcast.targets(InputKind.TEXT))
        assertFalse(view().targets.contains(first))
    }

    @Test
    fun sendingToTheGroupOfAPaneWithNoGroupChangesNothing() = runTest {
        broadcast.sendToFocusedGroup()

        assertEquals(BroadcastMode.Off, view().mode)
    }

    @Test
    fun anInvalidGroupNameIsRefusedAndStopTurnsTheBroadcastOff() = runTest {
        assertTrue(broadcast.assignFocused(" ") is Outcome.Failure)

        broadcast.toggle()
        broadcast.stop()

        assertEquals(BroadcastMode.Off, view().mode)
    }

    @Test
    fun theBroadcastBelongsToItsTab() = runTest {
        broadcast.toggle()
        val other = sessions.newSession()

        assertFalse(view().emitting)
        assertEquals(listOf(other), broadcast.targets(InputKind.TEXT))

        sessions.activate(first)
        assertTrue(view().emitting)
    }
}
