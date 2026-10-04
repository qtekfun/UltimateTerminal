// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SessionTabsTest {
    private fun tabs(count: Int): Sessions =
        (1..count).fold(Sessions()) { sessions, _ -> sessions.created().first }

    private fun Sessions.ids() = items.map { it.id.value }

    @Test
    fun aNewTabRemembersTheDistroItWasOpenedIn() {
        val (sessions, id) = Sessions().created(distroId = 7L)

        assertEquals(7L, sessions.items.single { it.id == id }.distroId)
        assertNull(Sessions().created().first.items.single().distroId)
    }

    @Test
    fun renamingStoresTheNormalizedName() {
        val sessions = tabs(2)
        val id = sessions.items[1].id

        val renamed = sessions.renamed(id, "  build server \n")

        assertEquals("build server", renamed.items[1].title)
        assertNull(renamed.items[0].title)
    }

    @Test
    fun aBlankNameGoesBackToTheDefaultOne() {
        val sessions = tabs(1)
        val id = sessions.items.single().id

        assertNull(sessions.renamed(id, "name").renamed(id, "   ").items.single().title)
        assertNull(sessions.renamed(id, "name").renamed(id, null).items.single().title)
    }

    @Test
    fun renamingToTheSameNameOrAnUnknownTabChangesNothing() {
        val sessions = tabs(1).let { it.renamed(it.items.single().id, "a") }

        assertSame(sessions, sessions.renamed(sessions.items.single().id, " a "))
        assertSame(sessions, sessions.renamed(SessionId(99), "b"))
    }

    @Test
    fun tabNamesDropControlCharactersAndAreLimited() {
        assertEquals("ab", TabTitle.normalize("a\u0000b\n"))
        assertNull(TabTitle.normalize("\t\n"))
        assertNull(TabTitle.normalize(null))
        assertEquals(TabTitle.MAX_LENGTH, TabTitle.normalize("x".repeat(100))?.length)
    }

    @Test
    fun cuttingANameNeverSplitsACharacterInTwo() {
        // The emoji is two chars; the 32nd char would be only its first half.
        val name = "x".repeat(TabTitle.MAX_LENGTH - 1) + "😀"

        assertEquals("x".repeat(TabTitle.MAX_LENGTH - 1), TabTitle.normalize(name))
    }

    @Test
    fun aNameCutInTheMiddleOfSpacesIsTrimmedAgain() {
        val name = "x".repeat(TabTitle.MAX_LENGTH - 2) + "  tail"

        assertEquals("x".repeat(TabTitle.MAX_LENGTH - 2), TabTitle.normalize(name))
    }

    @Test
    fun movingAnItemReordersTheOthers() {
        val sessions = tabs(4)

        assertEquals(listOf(3, 1, 2, 4), sessions.moved(SessionId(3), 0).ids())
        assertEquals(listOf(2, 3, 4, 1), sessions.moved(SessionId(1), 3).ids())
        assertEquals(listOf(1, 3, 2, 4), sessions.moved(SessionId(2), 2).ids())
    }

    @Test
    fun movingKeepsThePositionInsideTheListAndTheActiveTab() {
        val sessions = tabs(3).activated(SessionId(2))

        assertEquals(listOf(1, 3, 2), sessions.moved(SessionId(2), 99).ids())
        assertEquals(listOf(2, 1, 3), sessions.moved(SessionId(2), -5).ids())
        assertEquals(SessionId(2), sessions.moved(SessionId(2), 0).activeId)
    }

    @Test
    fun movingToWhereItAlreadyIsOrAnUnknownTabChangesNothing() {
        val sessions = tabs(3)

        assertSame(sessions, sessions.moved(SessionId(2), 1))
        assertSame(sessions, sessions.moved(SessionId(9), 0))
        assertEquals(Sessions(), Sessions().moved(SessionId(1), 0))
    }

    @Test
    fun nextAndPreviousWrapAroundTheEnds() {
        val sessions = tabs(3)

        assertEquals(SessionId(1), sessions.switched(TabSwitch.Next).activeId)
        assertEquals(SessionId(2), sessions.switched(TabSwitch.Previous).activeId)
        assertEquals(
            SessionId(3),
            sessions.activated(SessionId(1)).switched(TabSwitch.Previous).activeId
        )
        assertEquals(
            SessionId(2),
            sessions.activated(SessionId(1)).switched(TabSwitch.Next).activeId
        )
    }

    @Test
    fun withNoActiveTabNextGoesToTheFirstAndPreviousToTheLast() {
        val none = tabs(3).copy(activeId = null)

        assertEquals(SessionId(1), none.switched(TabSwitch.Next).activeId)
        assertEquals(SessionId(3), none.switched(TabSwitch.Previous).activeId)
    }

    @Test
    fun oneTabOrNoneHasNowhereToGo() {
        val one = tabs(1)

        assertSame(one, one.switched(TabSwitch.Next))
        assertSame(one, one.switched(TabSwitch.Previous))
        assertEquals(Sessions(), Sessions().switched(TabSwitch.Next))
    }

    @Test
    fun aNumberSelectsThatPositionCountingFromOne() {
        val sessions = tabs(3).moved(SessionId(3), 0)

        assertEquals(SessionId(3), sessions.switched(TabSwitch.Number(1)).activeId)
        assertEquals(SessionId(2), sessions.switched(TabSwitch.Number(3)).activeId)
    }

    @Test
    fun aNumberBeyondTheLastTabOrAnUnknownIdChangesNothing() {
        val sessions = tabs(2)

        assertSame(sessions, sessions.switched(TabSwitch.Number(5)))
        assertSame(sessions, sessions.switched(TabSwitch.Number(0)))
        assertSame(sessions, sessions.switched(TabSwitch.ById(SessionId(9))))
        assertEquals(SessionId(1), sessions.switched(TabSwitch.ById(SessionId(1))).activeId)
    }

    @Test
    fun aRunningShellNeedsConfirmationAnEndedOneDoesNot() {
        val sessions = tabs(2).exited(SessionId(1), 0)

        assertEquals(CloseAction.Close, sessions.closeAction(SessionId(1)))
        assertEquals(CloseAction.Confirm, sessions.closeAction(SessionId(2)))
        assertEquals(CloseAction.Ignore, sessions.closeAction(SessionId(9)))
    }

    @Test
    fun anOutOfRangeDragIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { dropIndex(2, 0f, listOf(10f, 10f)) }
    }
}
