// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.domain.terminal.TerminalLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private class Handle : SessionHandle {
    val resizes = mutableListOf<TerminalLayout>()
    var stopped = 0

    override fun resize(layout: TerminalLayout) {
        resizes += layout
    }

    override fun stop() {
        stopped++
    }
}

private class Shells : SessionFactory {
    val handles = mutableMapOf<SessionId, Handle>()
    private val exits = mutableMapOf<SessionId, (Int) -> Unit>()

    override fun start(
        id: SessionId,
        layout: TerminalLayout,
        onExit: (Int) -> Unit
    ): SessionHandle {
        exits[id] = onExit
        return Handle().also { handles[id] = it }
    }

    fun ends(id: SessionId) = exits.getValue(id)(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TabsControllerTest {
    private val shells = Shells()
    private val controller = SessionController(shells, { })
    private val distros = MutableStateFlow<List<Distro>>(emptyList())
    private val big = TerminalLayout(GridSize(200, 50), 9, 18)

    private fun runTabs(block: suspend (TabsController) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            block(TabsController(controller, distros, backgroundScope))
        }

    @Test
    fun theBarFollowsTheSessionsAndTheDistros() = runTabs { tabs ->
        distros.value = listOf(distro(4, "Alpine"))
        val id = controller.newSession(distroId = 4L)
        controller.newSession()

        assertEquals(listOf(1, 2), tabs.tabs.value.map { it.position })
        assertEquals("Alpine", tabs.tabs.value.first { it.id == id }.distroName)
        assertEquals(listOf(false, true), tabs.tabs.value.map { it.active })
    }

    @Test
    fun aNewTabOpensInTheDefaultDistroWhenThereIsOne() = runTabs { tabs ->
        distros.value = listOf(distro(1), distro(2, isDefault = true))

        tabs.newTab()

        assertEquals(2L, controller.state.value.items.single().distroId)
    }

    @Test
    fun withoutADefaultDistroANewTabIsTheAndroidShell() = runTabs { tabs ->
        tabs.newTab()

        assertNull(controller.state.value.items.single().distroId)
    }

    @Test
    fun theUserCanChooseTheShellOrAnotherDistro() = runTabs { tabs ->
        distros.value = listOf(distro(1, isDefault = true), distro(2))

        tabs.newTabIn(null)
        tabs.newTabIn(2L)

        assertEquals(listOf(null, 2L), controller.state.value.items.map { it.distroId })
    }

    @Test
    fun theChoicesFollowTheInstalledDistros() = runTabs { tabs ->
        assertEquals(listOf(DistroOption(null, null)), tabs.distroChoices.value)

        distros.value = listOf(distro(3, "Ubuntu"))

        assertEquals(DistroOption(3, "Ubuntu"), tabs.distroChoices.value.last())
    }

    @Test
    fun closingARunningTabWaitsForTheUser() = runTabs { tabs ->
        val id = controller.newSession()

        tabs.requestClose(id)

        assertEquals(id, tabs.closeConfirmation.value)
        assertEquals(1, controller.state.value.items.size)
        assertEquals(0, shells.handles.getValue(id).stopped)
    }

    @Test
    fun confirmingClosesOnlyThatTabAndLeavesTheOthersRunning() = runTabs { tabs ->
        val first = controller.newSession()
        val second = controller.newSession()

        tabs.requestClose(first)
        tabs.confirmClose()

        assertEquals(listOf(second), controller.state.value.items.map { it.id })
        assertEquals(1, shells.handles.getValue(first).stopped)
        assertEquals(0, shells.handles.getValue(second).stopped)
        assertNull(tabs.closeConfirmation.value)
    }

    @Test
    fun decliningKeepsTheTabAndItsShell() = runTabs { tabs ->
        val id = controller.newSession()

        tabs.requestClose(id)
        tabs.dismissClose()

        assertNull(tabs.closeConfirmation.value)
        assertEquals(0, shells.handles.getValue(id).stopped)
        assertEquals(1, controller.state.value.items.size)
    }

    @Test
    fun askingToCloseAlwaysWaitsEvenWhenTheShellEnded() = runTabs { tabs ->
        val id = controller.newSession()
        shells.ends(id)

        tabs.askToClose(id)

        assertEquals(id, tabs.closeConfirmation.value)
        assertEquals(1, controller.state.value.items.size)
        tabs.confirmClose()
        assertEquals(emptyList<SessionInfo>(), controller.state.value.items)
    }

    @Test
    fun askingToCloseAnUnknownTabDoesNothing() = runTabs { tabs ->
        controller.newSession()

        tabs.askToClose(SessionId(999))

        assertNull(tabs.closeConfirmation.value)
    }

    @Test
    fun aTabWhoseShellEndedClosesWithoutAsking() = runTabs { tabs ->
        val id = controller.newSession()
        shells.ends(id)

        tabs.requestClose(id)

        assertNull(tabs.closeConfirmation.value)
        assertEquals(emptyList<SessionInfo>(), controller.state.value.items)
    }

    @Test
    fun closingAnUnknownTabOrConfirmingNothingChangesNothing() = runTabs { tabs ->
        val id = controller.newSession()

        tabs.requestClose(SessionId(99))
        tabs.confirmClose()

        assertNull(tabs.closeConfirmation.value)
        assertEquals(listOf(id), controller.state.value.items.map { it.id })
    }

    @Test
    fun theShortcutClosesTheActiveTabAndDoesNothingWithoutOne() = runTabs { tabs ->
        tabs.requestCloseActive()
        assertNull(tabs.closeConfirmation.value)

        controller.newSession()
        val active = controller.newSession()
        tabs.requestCloseActive()

        assertEquals(active, tabs.closeConfirmation.value)
    }

    @Test
    fun aQuestionAboutATabThatWasClosedMeanwhileDisappears() = runTabs { tabs ->
        val id = controller.newSession()
        tabs.requestClose(id)

        controller.close(id)

        assertNull(tabs.closeConfirmation.value)
    }

    @Test
    fun renamingAndMovingGoThroughTheSessions() = runTabs { tabs ->
        val first = controller.newSession()
        val second = controller.newSession()

        tabs.rename(first, "db")
        tabs.move(first, 1)

        assertEquals(listOf(second, first), controller.state.value.items.map { it.id })
        assertEquals("db", tabs.tabs.value.last().title)
    }

    @Test
    fun switchingBringsAnotherShellToTheFrontAtTheCurrentSize() = runTabs { tabs ->
        val first = controller.newSession()
        val second = controller.newSession()
        controller.onLayout(big)

        tabs.switchTo(TabSwitch.Next)

        assertEquals(first, controller.state.value.activeId)
        assertEquals(listOf(big), shells.handles.getValue(first).resizes)
        assertEquals(listOf(big), shells.handles.getValue(second).resizes)
        assertEquals(0, shells.handles.getValue(second).stopped)
    }

    @Test
    fun switchingToTheSameTabResizesNothing() = runTabs { tabs ->
        val id = controller.newSession()
        controller.onLayout(big)

        tabs.switchTo(TabSwitch.ById(id))

        assertEquals(listOf(big), shells.handles.getValue(id).resizes)
    }
}
