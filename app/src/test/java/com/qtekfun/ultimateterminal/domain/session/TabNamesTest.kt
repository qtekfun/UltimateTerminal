// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabNamesTest {
    private fun tab(id: Int, title: String? = null, distro: String? = null) = TabItem(
        id = SessionId(id),
        title = title,
        position = id,
        running = true,
        distroName = distro,
        active = false
    )

    @Test
    fun aTabInADistroIsCalledAfterIt() {
        assertEquals(
            listOf<TabName>(TabName.InDistro("Alpine", 1)),
            tabNames(listOf(tab(1, distro = "Alpine")))
        )
    }

    @Test
    fun theNameTheUserTypedAlwaysWins() {
        assertEquals(
            listOf<TabName>(TabName.Custom("logs")),
            tabNames(listOf(tab(1, title = "logs", distro = "Alpine")))
        )
    }

    @Test
    fun aTabInNoDistroKeepsItsPlainName() {
        assertEquals(listOf<TabName>(TabName.Plain(4)), tabNames(listOf(tab(4))))
    }

    @Test
    fun severalTabsOfOneDistroAreNumberedInBarOrder() {
        val names = tabNames(
            listOf(tab(1, distro = "Alpine"), tab(2, distro = "Alpine"), tab(3, distro = "Alpine"))
        )

        assertEquals(
            listOf<TabName>(
                TabName.InDistro("Alpine", 1),
                TabName.InDistro("Alpine", 2),
                TabName.InDistro("Alpine", 3)
            ),
            names
        )
    }

    @Test
    fun aRenamedTabDoesNotTakeANumberFromItsDistro() {
        val names = tabNames(
            listOf(tab(1, title = "web", distro = "Alpine"), tab(2, distro = "Alpine"))
        )

        assertEquals(
            listOf<TabName>(TabName.Custom("web"), TabName.InDistro("Alpine", 1)),
            names
        )
    }

    @Test
    fun eachDistroCountsItsOwnTabs() {
        val names = tabNames(
            listOf(tab(1, distro = "Alpine"), tab(2, distro = "Debian"), tab(3, distro = "Alpine"))
        )

        assertEquals(
            listOf<TabName>(
                TabName.InDistro("Alpine", 1),
                TabName.InDistro("Debian", 1),
                TabName.InDistro("Alpine", 2)
            ),
            names
        )
    }
}
