// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.profile.PaneLook
import com.qtekfun.ultimateterminal.domain.profile.PaneOpening
import com.qtekfun.ultimateterminal.domain.profile.PaneSpec
import com.qtekfun.ultimateterminal.domain.profile.PaneTarget
import com.qtekfun.ultimateterminal.domain.profile.PlannedNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenedTabTest {
    private fun pane(distro: Long?, command: String? = null) = PlannedNode.Pane(
        PaneSpec(
            if (distro == null) PaneTarget.AndroidShell else PaneTarget.InDistro(distro, "d$distro", null),
            PaneLook(scrollbackLines = 1000),
            command?.let { "$it\r" }
        ),
        command
    )

    private val three = PlannedNode.Split(
        SplitOrientation.VERTICAL,
        0.3f,
        pane(1),
        PlannedNode.Split(SplitOrientation.HORIZONTAL, 0.6f, pane(2), pane(null))
    )

    @Test
    fun theNewTabHasOneSessionPerPaneInReadingOrderAndTheFirstIsTheTab() {
        val (opened, ids) = Sessions().created(9).first.openedTab(three)

        assertEquals(listOf(SessionId(2), SessionId(3), SessionId(4)), ids)
        assertEquals(listOf<Long?>(9, 1, 2, null), opened.items.map { it.distroId })
        assertEquals(ids.first(), opened.activeId)
        assertEquals(5, opened.nextId)
        assertEquals(listOf(SessionId(1), ids.first()), opened.tabs.map { it.id })
        assertEquals(ids, opened.paneIdsOf(ids.first()))
        assertEquals(ids.first(), opened.focusOf(ids.first()))
    }

    @Test
    fun theTreeKeepsTheOrientationsAndTheRatios() {
        val (opened, ids) = Sessions().openedTab(three)

        assertEquals(
            PaneNode.Branch(
                SplitOrientation.VERTICAL,
                0.3f,
                PaneNode.Leaf(ids[0]),
                PaneNode.Branch(
                    SplitOrientation.HORIZONTAL,
                    0.6f,
                    PaneNode.Leaf(ids[1]),
                    PaneNode.Leaf(ids[2])
                )
            ),
            opened.treeOf(ids[0])
        )
        assertFalse(opened.panes.getValue(ids[0]).zoomed)
    }

    @Test
    fun aSinglePaneIsAPlainTabNotASplitOne() {
        val (opened, ids) = Sessions().openedTab(pane(5))

        assertEquals(1, ids.size)
        assertTrue(opened.panes.isEmpty())
        assertEquals(5L, opened.items.single().distroId)
        assertEquals(ids.single(), opened.activeId)
    }

    @Test
    fun aSplitWithAnOpeningStartsTheNewPaneWhereTheOpeningSaysAndWithoutOneInTheSourcesDistro() {
        val base = Sessions().created(3).first

        val plain = base.split(SplitOrientation.VERTICAL)!!.first
        val android = base.split(SplitOrientation.VERTICAL, PaneOpening(pane(null).spec))!!.first
        val other = base.split(SplitOrientation.VERTICAL, PaneOpening(pane(8).spec))!!.first

        assertEquals(listOf<Long?>(3, 3), plain.items.map { it.distroId })
        assertEquals(listOf<Long?>(3, null), android.items.map { it.distroId })
        assertEquals(listOf<Long?>(3, 8), other.items.map { it.distroId })
        assertNull(Sessions().split(SplitOrientation.VERTICAL, PaneOpening(pane(8).spec)))
    }
}
