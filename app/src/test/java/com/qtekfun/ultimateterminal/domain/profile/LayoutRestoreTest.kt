// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.data.local.LayoutCodec
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.distro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutRestoreTest {
    private val alpine = distro(1, "Alpine", isDefault = true)
    private val debian = distro(2, "Debian")
    private val web = Profile(
        id = 7,
        name = "web",
        distroId = 2,
        colorSchemeId = "nord",
        startupCommand = "top"
    )
    private val planner = LayoutRestorePlanner(
        resolver = PaneSpecResolver(listOf(alpine, debian), setOf("nord"), emptySet()),
        profiles = mapOf(7L to web)
    )

    private fun ready(layout: Layout) = (planner.plan(layout) as LayoutRestore.Ready).plan

    private fun split(ratio: Float, first: LayoutNode, second: LayoutNode) =
        LayoutNode.Split(SplitOrientation.VERTICAL, ratio, first, second)

    private fun balanced(depth: Int): LayoutNode = if (depth == 0) {
        LayoutNode.Pane()
    } else {
        split(0.5f, balanced(depth - 1), balanced(depth - 1))
    }

    private fun chain(splits: Int): LayoutNode = if (splits == 0) {
        LayoutNode.Pane()
    } else {
        split(0.5f, LayoutNode.Pane(), chain(splits - 1))
    }

    @Test
    fun aSinglePaneOpensTheDefaultProfile() {
        val plan = ready(Layout(1, "one", LayoutNode.Pane()))

        assertEquals("one", plan.layoutName)
        assertEquals(1, plan.panes.size)
        assertEquals(PaneTarget.InDistro(1, "Alpine", null), plan.panes[0].target)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun theShapeTheProfilesAndTheCommandsComeBackInReadingOrder() {
        val saved = Layout(
            1,
            "ops",
            LayoutNode.Split(
                SplitOrientation.HORIZONTAL,
                0.7f,
                LayoutNode.Pane(profileId = 7),
                LayoutNode.Pane(command = "ls")
            )
        )

        val plan = ready(saved)
        val root = plan.root as PlannedNode.Split

        assertEquals(SplitOrientation.HORIZONTAL, root.orientation)
        assertEquals(0.7f, root.ratio)
        assertEquals(PaneTarget.InDistro(2, "Debian", null), plan.panes[0].target)
        assertEquals("nord", plan.panes[0].look.colorSchemeId)
        assertEquals("top\r", plan.panes[0].startupInput)
        assertEquals("ls\r", plan.panes[1].startupInput)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun aProfileThatWasDeletedGivesTheDefaultOneAndANoticeOnThatPane() {
        val plan = ready(
            Layout(
                1,
                "x",
                split(0.5f, LayoutNode.Pane(), LayoutNode.Pane(profileId = 99, command = "top"))
            )
        )

        assertEquals(listOf(LayoutNotice.ForPane(1, PaneNotice.ProfileMissing(99))), plan.notices)
        assertEquals(PaneTarget.InDistro(1, "Alpine", null), plan.panes[1].target)
        // The layout's own command still applies: only the profile was lost.
        assertEquals("top\r", plan.panes[1].startupInput)
    }

    @Test
    fun aPaneThatCannotOpenAsSavedStillOpensPlainlyWithTheReason() {
        val plan = ready(Layout(1, "x", LayoutNode.Pane(command = "a\nb")))

        assertEquals(null, plan.panes[0].startupInput)
        assertEquals(
            listOf(
                LayoutNotice.ForPane(
                    0,
                    PaneNotice.Degraded(
                        ProfileProblem.InvalidStartupCommand(StartupCommandFault.MULTIPLE_LINES)
                    )
                )
            ),
            plan.notices
        )
    }

    @Test
    fun aSplitTooLopsidedForUsablePanesIsEvenedOutAndOneNearTheMiddleSnaps() {
        val lopsided = ready(Layout(1, "x", split(0.02f, LayoutNode.Pane(), LayoutNode.Pane())))
        val nearly = ready(Layout(1, "x", split(0.51f, LayoutNode.Pane(), LayoutNode.Pane())))

        assertEquals(0.1f, (lopsided.root as PlannedNode.Split).ratio)
        assertEquals(listOf(LayoutNotice.RatioAdjusted(0)), lopsided.notices)
        assertEquals(0.5f, (nearly.root as PlannedNode.Split).ratio)
        assertTrue(nearly.notices.isEmpty())
    }

    @Test
    fun splitsAreNumberedInTheOrderTheyAreVisited() {
        val nested =
            split(0.5f, split(0.01f, LayoutNode.Pane(), LayoutNode.Pane()), LayoutNode.Pane())

        val plan = ready(Layout(1, "x", split(0.99f, nested, LayoutNode.Pane())))

        assertEquals(
            listOf(LayoutNotice.RatioAdjusted(0), LayoutNotice.RatioAdjusted(2)),
            plan.notices
        )
    }

    @Test
    fun aLayoutWithTooManyPanesOrTooDeepIsRefusedNotOpened() {
        assertEquals(
            LayoutRestore.Refused(LayoutRefusal.TOO_MANY_PANES),
            planner.plan(Layout(1, "x", balanced(5)))
        )
        assertEquals(
            LayoutRestore.Refused(LayoutRefusal.TOO_DEEP),
            planner.plan(Layout(1, "x", chain(LayoutRestorePlanner.MAX_DEPTH + 1)))
        )
        // The limits themselves are fine.
        assertEquals(16, ready(Layout(1, "x", balanced(4))).panes.size)
        assertEquals(
            LayoutRestorePlanner.MAX_DEPTH + 1,
            ready(Layout(1, "x", chain(LayoutRestorePlanner.MAX_DEPTH))).panes.size
        )
    }

    @Test
    fun theStoredJsonOfTodayAndOfEarlierVersionsRestoresTheSame() {
        val layout = Layout(
            1,
            "x",
            LayoutNode.Split(
                SplitOrientation.VERTICAL,
                0.4f,
                LayoutNode.Pane(profileId = 7),
                LayoutNode.Pane(command = "ls")
            )
        )
        val stored = LayoutCodec.encode(layout.root)
        // How T05 stored panes before profiles and commands were used: bare panes, and a key a later
        // version might add.
        val old = """{"type":"split","orientation":"VERTICAL","ratio":0.5,""" +
            """"first":{"type":"pane"},"second":{"type":"pane","future":1}}"""

        val again = LayoutCodec.decode(stored)
        val earlier = LayoutCodec.decode(old)

        assertNotNull(again)
        assertNotNull(earlier)
        assertEquals(ready(layout), ready(Layout(1, "x", checkNotNull(again))))
        assertEquals(2, ready(Layout(2, "old", checkNotNull(earlier))).panes.size)
    }
}
