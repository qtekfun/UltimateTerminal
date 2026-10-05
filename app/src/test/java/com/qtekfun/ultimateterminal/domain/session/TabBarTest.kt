// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.terminal.EdgeInsets
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal fun distro(
    id: Long,
    name: String = "d$id",
    state: DistroState = DistroState.READY,
    isDefault: Boolean = false
) = Distro(
    id = id,
    name = name,
    type = DistroType.DEBIAN,
    release = "12",
    directory = checkNotNull(FsPath.of("distros/$id").getOrNull()),
    defaultUser = "root",
    state = state,
    sizeBytes = 0,
    installedAt = Instant.EPOCH,
    isDefault = isDefault
)

class TabBarTest {
    @Test
    fun theBarListsTabsInOrderWithTheirStateAndDistro() {
        val (one, first) = Sessions().created(distroId = 2L)
        val (two, second) = one.created()
        val sessions = two.exited(first, 0).renamed(second, "logs").activated(first)

        val items = tabItems(sessions, listOf(distro(2, "Debian"), distro(3)))

        assertEquals(listOf(1, 2), items.map { it.position })
        assertEquals(listOf(false, true), items.map { it.running })
        assertEquals(listOf(true, false), items.map { it.active })
        assertEquals(listOf(null, "logs"), items.map { it.title })
        assertEquals(listOf("Debian", null), items.map { it.distroName })
    }

    @Test
    fun aTabWhoseDistroWasRemovedShowsNoDistro() {
        val sessions = Sessions().created(distroId = 9L).first

        assertNull(tabItems(sessions, emptyList()).single().distroName)
    }

    @Test
    fun noSessionsMeansNoTabs() {
        assertTrue(tabItems(Sessions(), listOf(distro(1))).isEmpty())
    }

    @Test
    fun theAndroidShellIsAlwaysAnOptionAndComesFirst() {
        assertEquals(listOf(DistroOption(null, null)), distroOptions(emptyList()))
    }

    @Test
    fun onlyReadyDistrosAreOptionsAndTheDefaultComesFirst() {
        val distros = listOf(
            distro(1, "a"),
            distro(2, "b", isDefault = true),
            distro(3, "c", state = DistroState.INSTALLING),
            distro(4, "d", state = DistroState.FAILED)
        )

        assertEquals(
            listOf(DistroOption(null, null), DistroOption(2, "b"), DistroOption(1, "a")),
            distroOptions(distros)
        )
    }

    @Test
    fun aNewTabOpensInTheDefaultDistroOnlyIfItIsUsable() {
        assertEquals(2L, defaultDistroId(listOf(distro(1), distro(2, isDefault = true))))
        assertNull(defaultDistroId(listOf(distro(1, isDefault = true, state = DistroState.FAILED))))
        assertNull(defaultDistroId(listOf(distro(1))))
        assertNull(defaultDistroId(emptyList()))
    }

    @Test
    fun theBarIsAColumnFromMediumWidthsOn() {
        assertEquals(TabBarPlacement.Top, tabBarPlacement(0))
        assertEquals(TabBarPlacement.Top, tabBarPlacement(WIDE_WINDOW_DP - 1))
        assertEquals(TabBarPlacement.Side, tabBarPlacement(WIDE_WINDOW_DP))
        assertEquals(TabBarPlacement.Side, tabBarPlacement(1280))
    }

    @Test
    fun theBarTakesItsSpaceFromTheEdgeItSitsOn() {
        val insets = EdgeInsets(left = 4, top = 10, right = 6, bottom = 20)

        assertEquals(EdgeInsets(4, 58, 6, 20), insets.reserveForTabBar(TabBarPlacement.Top, 48))
        assertEquals(EdgeInsets(196, 10, 6, 20), insets.reserveForTabBar(TabBarPlacement.Side, 192))
    }

    @Test
    fun aDragLessThanHalfATabStaysWhereItIs() {
        val sizes = listOf(100f, 100f, 100f)

        assertEquals(1, dropIndex(1, 0f, sizes))
        assertEquals(1, dropIndex(1, 49f, sizes))
        assertEquals(1, dropIndex(1, -49f, sizes))
    }

    @Test
    fun aDragPastTheNeighbourSwapsPlaces() {
        val sizes = listOf(100f, 100f, 100f)

        assertEquals(2, dropIndex(1, 51f, sizes))
        assertEquals(0, dropIndex(1, -51f, sizes))
        assertEquals(2, dropIndex(0, 160f, sizes))
    }

    @Test
    fun aDragBeyondTheEndsLandsOnTheFirstOrLastTab() {
        val sizes = listOf(100f, 100f, 100f)

        assertEquals(2, dropIndex(0, 5000f, sizes))
        assertEquals(0, dropIndex(2, -5000f, sizes))
    }

    @Test
    fun tabsOfDifferentSizesAreMeasuredOneByOne() {
        val sizes = listOf(50f, 200f, 50f)

        // The first tab is dragged to the middle of the second: x = 25 + 150 = 175.
        assertEquals(1, dropIndex(0, 150f, sizes))
        // Dragged to the third: x = 25 + 260 = 285 > 250.
        assertEquals(2, dropIndex(0, 260f, sizes))
        assertFalse(dropIndex(2, -10f, sizes) == 0)
    }

    @Test
    fun aLongPressThatStaysPutAsksToCloseAndOneThatMovesReorders() {
        assertEquals(TabLongPress.CONFIRM_CLOSE, resolveLongPress(0f, 8f))
        assertEquals(TabLongPress.CONFIRM_CLOSE, resolveLongPress(8f, 8f))
        assertEquals(TabLongPress.REORDER, resolveLongPress(8.1f, 8f))
    }
}
