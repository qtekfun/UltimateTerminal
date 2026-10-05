// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.DividerPath
import com.qtekfun.ultimateterminal.domain.session.SessionController
import com.qtekfun.ultimateterminal.domain.session.SessionFactory
import com.qtekfun.ultimateterminal.domain.session.SessionHandle
import com.qtekfun.ultimateterminal.domain.session.distro
import com.qtekfun.ultimateterminal.domain.session.ratioSet
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import com.qtekfun.ultimateterminal.fakes.FakeLayoutRepository
import com.qtekfun.ultimateterminal.fakes.FakeProfileRepository
import com.qtekfun.ultimateterminal.fakes.FakeSettingsRepository
import com.qtekfun.ultimateterminal.fakes.StaticDistros
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutSaverTest {
    private val sessions = SessionController(
        SessionFactory { _, _, _ ->
            object : SessionHandle {
                override fun resize(layout: TerminalLayout) = Unit

                override fun stop() = Unit
            }
        },
        { }
    )
    private val work = Profile(id = 7, name = "Work", startupCommand = "tmux")
    private val layouts = FakeLayoutRepository()
    private val saver = LayoutSaver(sessions, layouts)
    private val opener = PaneOpener(
        sessions,
        FakeProfileRepository(listOf(work)),
        StaticDistros(listOf(distro(1, "Alpine", isDefault = true))),
        FakeSettingsRepository()
    )

    private suspend fun saved() = layouts.observeAll().first()

    @Test
    fun withNoTabThereIsNothingToSave() = runTest {
        assertEquals(Outcome.Failure(DomainError.NotFound), saver.save("x"))
    }

    @Test
    fun theTabIsSavedWithItsShapeItsProfilesAndItsCommands() = runTest {
        opener.open(work, OpenAs.NEW_TAB)
        // A plain split of a pane opened with a profile inherits the profile (D-FIX-8).
        sessions.splitActive(SplitOrientation.VERTICAL)
        sessions.edit { ratioSet(DividerPath(emptyList()), 0.3f) }

        val result = saver.save("  dev  ")

        val expected = LayoutNode.Split(
            SplitOrientation.VERTICAL,
            0.3f,
            LayoutNode.Pane(profileId = 7),
            LayoutNode.Pane(profileId = 7)
        )
        assertEquals(Layout(1, "dev", expected), (result as Outcome.Success).value)
        assertEquals(listOf(Layout(1, "dev", expected)), saved())
    }

    @Test
    fun aPaneOpenedFromALayoutSavesItsOwnCommandNotTheProfilesOne() = runTest {
        val original = Layout(
            5,
            "src",
            LayoutNode.Split(
                SplitOrientation.HORIZONTAL,
                0.5f,
                LayoutNode.Pane(profileId = 7, command = "htop"),
                LayoutNode.Pane(profileId = 7)
            )
        )
        opener.restore(original)

        val again = (saver.save("copy") as Outcome.Success).value

        assertEquals(original.root, again.root)
    }

    @Test
    fun aNameInUseIsRefusedUnlessTheUserAsksToReplaceIt() = runTest {
        opener.open(null, OpenAs.NEW_TAB)
        saver.save("Dev")
        sessions.splitActive(SplitOrientation.VERTICAL)

        assertEquals(Outcome.Failure(DomainError.NameTaken("dev")), saver.save("dev"))
        assertEquals(1, saved().size)

        val replaced = (saver.save("dev", replace = true) as Outcome.Success).value
        assertEquals(1, replaced.id)
        assertEquals("dev", replaced.name)
        assertTrue(replaced.root is LayoutNode.Split)
        assertEquals(listOf(replaced), saved())
    }

    @Test
    fun replacingANameThatDoesNotExistJustSaves() = runTest {
        opener.open(null, OpenAs.NEW_TAB)

        val created = (saver.save("fresh", replace = true) as Outcome.Success).value

        assertEquals("fresh", created.name)
        assertEquals(1, saved().size)
    }

    @Test
    fun anInvalidNameIsRefusedAndNothingIsStored() = runTest {
        opener.open(null, OpenAs.NEW_TAB)

        assertTrue(saver.save("   ") is Outcome.Failure)
        assertTrue(saved().isEmpty())
    }
}
