// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.PaneNode
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionFactory
import com.qtekfun.ultimateterminal.domain.session.SessionHandle
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.distro
import com.qtekfun.ultimateterminal.domain.session.paneIdsOf
import com.qtekfun.ultimateterminal.domain.session.tabOf
import com.qtekfun.ultimateterminal.domain.session.treeOf
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.fakes.FakeProfileRepository
import com.qtekfun.ultimateterminal.fakes.FakeSettingsRepository
import com.qtekfun.ultimateterminal.fakes.StaticDistros
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Opening a profile or a layout through the real session controller, with fakes at the edges. */
class PaneOpenerTest {
    private val started = mutableListOf<SessionId>()
    private val sessions = SessionController(
        SessionFactory { id, _, _ ->
            started += id
            object : SessionHandle {
                override fun resize(layout: TerminalLayout) = Unit

                override fun stop() = Unit
            }
        },
        { }
    )
    private val alpine = distro(1, "Alpine", isDefault = true)
    private val debian = distro(2, "Debian")
    private val busy = distro(3, "Busy", state = DistroState.INSTALLING)
    private val work =
        Profile(id = 7, name = "Work", distroId = 2, user = "dev", startupCommand = "tmux")
    private val profiles = FakeProfileRepository(listOf(work))
    private val opener = PaneOpener(
        sessions,
        profiles,
        StaticDistros(listOf(alpine, debian, busy)),
        FakeSettingsRepository()
    )

    @Test
    fun aProfileOpensAsANewTabInItsDistroAndRemembersWhatItWasOpenedWith() = runTest {
        val result = opener.open(work, OpenAs.NEW_TAB)

        assertEquals(OpenResult.Opened(emptyList()), result)
        val tab = sessions.state.value.activeId!!
        assertEquals(2L, sessions.state.value.items.single().distroId)
        val opening = sessions.openingOf(tab)!!
        assertEquals(PaneTarget.InDistro(2, "Debian", "dev"), opening.spec.target)
        assertEquals("tmux\r", opening.spec.startupInput)
        assertEquals(listOf(tab), started)
    }

    @Test
    fun aProfileSplitsTheActivePaneInItsOwnDistroNotTheSourcesOne() = runTest {
        sessions.newSession(1)

        val right = opener.open(work, OpenAs.SPLIT_RIGHT)
        val down = opener.open(work.copy(distroId = null, user = null), OpenAs.SPLIT_DOWN)

        assertEquals(OpenResult.Opened(emptyList()), right)
        assertEquals(OpenResult.Opened(emptyList()), down)
        val state = sessions.state.value
        val tab = state.tabOf(checkNotNull(state.activeId))
        val tree = state.treeOf(tab) as PaneNode.Branch
        assertEquals(SplitOrientation.VERTICAL, tree.orientation)
        assertEquals(listOf<Long?>(1, 2, 1), state.items.map { it.distroId })
        assertEquals(3, state.paneIdsOf(tab).size)
    }

    @Test
    fun aProfileThatCannotOpenIsRejectedAndNothingStarts() = runTest {
        val gone = work.copy(distroId = 99)
        val notReady = work.copy(distroId = 3)

        assertEquals(
            OpenResult.Rejected(ProfileProblem.UnknownDistro(99)),
            opener.open(gone, OpenAs.NEW_TAB)
        )
        assertEquals(
            OpenResult.Rejected(ProfileProblem.DistroNotReady("Busy")),
            opener.open(notReady, OpenAs.NEW_TAB)
        )
        assertTrue(started.isEmpty())
    }

    @Test
    fun aSplitWithNoTabToSplitSaysSoAndStartsNothing() = runTest {
        assertEquals(OpenResult.NothingToSplit, opener.open(work, OpenAs.SPLIT_RIGHT))
        assertTrue(started.isEmpty())
    }

    @Test
    fun noProfileOpensTheDefaultDistroWithTheGlobalLook() = runTest {
        opener.open(null, OpenAs.NEW_TAB)

        val opening = sessions.openingOf(sessions.state.value.activeId!!)!!
        assertEquals(PaneTarget.InDistro(1, "Alpine", null), opening.spec.target)
        assertNull(opening.spec.profileId)
    }

    @Test
    fun aLayoutOpensAsOneNewTabWithAPaneForEachSavedPane() = runTest {
        val layout = Layout(
            1,
            "ops",
            LayoutNode.Split(
                SplitOrientation.VERTICAL,
                0.3f,
                LayoutNode.Pane(profileId = 7, command = "htop"),
                LayoutNode.Pane()
            )
        )

        val result = opener.restore(layout)

        assertEquals(RestoreResult.Opened(emptyList()), result)
        val state = sessions.state.value
        val tab = state.tabOf(state.activeId!!)
        assertEquals(1, state.tabs.size)
        val ids = state.paneIdsOf(tab)
        assertEquals(2, ids.size)
        assertEquals(started, ids)
        assertEquals("htop\r", sessions.openingOf(ids[0])!!.spec.startupInput)
        assertEquals(
            PaneTarget.InDistro(2, "Debian", "dev"),
            sessions.openingOf(ids[0])!!.spec.target
        )
        assertEquals(0.3f, (state.treeOf(tab) as PaneNode.Branch).ratio)
        assertEquals(
            PaneTarget.InDistro(1, "Alpine", null),
            sessions.openingOf(ids[1])!!.spec.target
        )
    }

    @Test
    fun aLayoutWhosePaneNamesAGoneProfileStillOpensWithANoticeOfWhere() = runTest {
        val layout = Layout(
            1,
            "old",
            LayoutNode.Split(
                SplitOrientation.HORIZONTAL,
                0.5f,
                LayoutNode.Pane(),
                LayoutNode.Pane(profileId = 42, command = "top")
            )
        )

        val result = opener.restore(layout) as RestoreResult.Opened

        assertEquals(
            listOf(LayoutNotice.ForPane(1, PaneNotice.ProfileMissing(42))),
            result.notices
        )
        val ids = sessions.state.value.let { it.paneIdsOf(it.tabOf(it.activeId!!)) }
        // The command the layout gave the pane survives the missing profile.
        assertEquals("top\r", sessions.openingOf(ids[1])!!.spec.startupInput)
    }

    @Test
    fun aLayoutThatIsTooBigIsRefusedAndNothingStarts() = runTest {
        var node: LayoutNode = LayoutNode.Pane()
        repeat(LayoutRestorePlanner.MAX_DEPTH + 1) {
            node = LayoutNode.Split(SplitOrientation.VERTICAL, 0.5f, node, LayoutNode.Pane())
        }

        val result = opener.restore(Layout(1, "deep", node))

        assertEquals(RestoreResult.Refused(LayoutRefusal.TOO_DEEP), result)
        assertTrue(started.isEmpty())
    }
}
