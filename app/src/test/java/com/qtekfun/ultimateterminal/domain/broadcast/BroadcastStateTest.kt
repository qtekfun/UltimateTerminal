// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.broadcast

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.session.SessionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BroadcastStateTest {
    private val a = SessionId(1)
    private val b = SessionId(2)
    private val c = SessionId(3)
    private val panes = listOf(a, b, c)

    private fun BroadcastState.assigned(pane: SessionId, group: String?) =
        (assign(pane, group) as Outcome.Success).value

    @Test
    fun withBroadcastOffOnlyThePaneThatHasTheKeyboardReceivesIt() {
        assertEquals(listOf(b), BroadcastState().targets(b, panes, InputKind.TEXT))
    }

    @Test
    fun allPanesReceiveTextInTheOrderOfTheTab() {
        val state = BroadcastState(BroadcastMode.AllPanes)

        assertEquals(panes, state.targets(b, panes, InputKind.TEXT))
    }

    @Test
    fun aSinglePaneHasNothingToBroadcastTo() {
        val state = BroadcastState(BroadcastMode.AllPanes)

        assertEquals(listOf(a), state.targets(a, listOf(a), InputKind.TEXT))
        assertFalse(state.isEmitting(listOf(a)))
    }

    @Test
    fun aPaneThatIsNotInTheTabReceivesOnlyItself() {
        val state = BroadcastState(BroadcastMode.AllPanes)

        assertEquals(listOf(SessionId(9)), state.targets(SessionId(9), panes, InputKind.TEXT))
    }

    @Test
    fun textOnlyKeepsControlKeysInTheActivePaneUnlessTurnedOff() {
        val state = BroadcastState(BroadcastMode.AllPanes)

        assertTrue(state.textOnly)
        assertEquals(listOf(a), state.targets(a, panes, InputKind.CONTROL))
        assertEquals(panes, state.withTextOnly(false).targets(a, panes, InputKind.CONTROL))
        assertEquals(panes, state.targets(a, panes, InputKind.TEXT))
    }

    @Test
    fun aGroupReceivesOnlyFromItsOwnMembers() {
        val state = BroadcastState(BroadcastMode.Group("web"))
            .assigned(a, "web")
            .assigned(c, "web")

        assertEquals(listOf(a, c), state.targets(a, panes, InputKind.TEXT))
        assertEquals(listOf(a, c), state.targets(c, panes, InputKind.TEXT))
        // Typing in a pane outside the group does not leak into it.
        assertEquals(listOf(b), state.targets(b, panes, InputKind.TEXT))
    }

    @Test
    fun theToggleSwitchesBetweenOffAndAllPanes() {
        val off = BroadcastState()

        assertEquals(BroadcastMode.AllPanes, off.toggled().mode)
        assertEquals(BroadcastMode.Off, off.toggled().toggled().mode)
        assertEquals(BroadcastMode.Off, off.withMode(BroadcastMode.Group("x")).toggled().mode)
    }

    @Test
    fun theUiCanTellWhenTypingWillReachOtherPanes() {
        val grouped = BroadcastState(BroadcastMode.Group("web")).assigned(a, "web")

        assertFalse(BroadcastState().isEmitting(panes))
        assertTrue(BroadcastState(BroadcastMode.AllPanes).isEmitting(panes))
        // A group of one reaches nobody else.
        assertFalse(grouped.isEmitting(panes))
        assertTrue(grouped.assigned(b, "web").isEmitting(panes))
    }

    @Test
    fun aGroupNameIsCheckedAndAPaneCanLeaveItsGroup() {
        val named = BroadcastState().assigned(a, "  web ")

        assertEquals("web", named.groupOf(a))
        assertEquals(setOf("web"), named.groups())
        assertNull(named.assigned(a, null).groupOf(a))
        assertEquals(
            Outcome.Failure(DomainError.InvalidName(" ")),
            BroadcastState().assign(a, " ")
        )
    }

    @Test
    fun closedPanesAreForgottenAndABroadcastToAnEmptyGroupTurnsOff() {
        val state = BroadcastState(BroadcastMode.Group("web")).assigned(a, "web").assigned(b, "db")

        val onlyB = state.pruned(listOf(b, c))

        assertNull(onlyB.groupOf(a))
        assertEquals("db", onlyB.groupOf(b))
        assertEquals(BroadcastMode.Off, onlyB.mode)
        assertEquals(BroadcastMode.Group("web"), state.pruned(panes).mode)
        assertEquals(
            BroadcastMode.AllPanes,
            BroadcastState(BroadcastMode.AllPanes).pruned(emptyList()).mode
        )
    }
}
