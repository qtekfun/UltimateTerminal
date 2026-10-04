// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HostRegistryTest {
    private val registry = HostRegistry<String>()
    private val active = MutableStateFlow<SessionId?>(null)

    @Test
    fun aHostRegisteredAfterTheStateThatNamesItsSessionIsStillFollowed() = runTest {
        // The bug: the state publishes the new tab first and its host is created afterwards.
        registry.follow(active).test {
            assertNull(awaitItem())

            // Its host does not exist yet: still nothing to follow, and nothing new to say.
            active.value = SessionId(1)
            expectNoEvents()

            registry.put(SessionId(1), "host one")
            assertEquals("host one", awaitItem())
        }
    }

    @Test
    fun switchingTheActiveSessionFollowsTheOtherHost() = runTest {
        registry.put(SessionId(1), "one")
        registry.put(SessionId(2), "two")
        active.value = SessionId(1)

        registry.follow(active).test {
            assertEquals("one", awaitItem())

            active.value = SessionId(2)
            assertEquals("two", awaitItem())
        }
    }

    @Test
    fun aHostThatIsNotTheActiveOneDoesNotEmitAgain() = runTest {
        registry.put(SessionId(1), "one")
        active.value = SessionId(1)

        registry.follow(active).test {
            assertEquals("one", awaitItem())

            registry.put(SessionId(2), "two")
            expectNoEvents()
        }
    }

    @Test
    fun theHostOfAClosedSessionIsNoLongerFollowed() = runTest {
        registry.put(SessionId(1), "one")
        active.value = SessionId(1)

        registry.follow(active).test {
            assertEquals("one", awaitItem())

            registry.remove(SessionId(1))
            assertNull(awaitItem())
        }
    }

    @Test
    fun removingAHostThatWasNeverThereChangesNothing() {
        val before = registry.changes.value

        registry.remove(SessionId(9))

        assertEquals(before, registry.changes.value)
    }

    @Test
    fun theRegistryAnswersByIdAndListsItsHosts() {
        registry.put(SessionId(1), "one")
        registry.put(SessionId(2), "two")

        assertEquals("two", registry[SessionId(2)])
        assertNull(registry[SessionId(3)])
        assertNull(registry[null])
        assertEquals(listOf("one", "two"), registry.values)
    }
}
