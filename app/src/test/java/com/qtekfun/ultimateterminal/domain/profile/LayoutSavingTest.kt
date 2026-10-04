// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.session.PaneNode
import com.qtekfun.ultimateterminal.domain.session.SessionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LayoutSavingTest {
    private val one = SessionId(1)
    private val two = SessionId(2)
    private val tree = PaneNode.Branch(
        SplitOrientation.VERTICAL,
        0.4f,
        PaneNode.Leaf(one),
        PaneNode.Leaf(two)
    )
    private val describe = { id: SessionId ->
        if (id == one) PaneDescription(profileId = 3, command = " top ") else PaneDescription()
    }

    private fun build(
        name: String,
        existing: List<Layout> = emptyList(),
        replacing: Long = 0L,
        describe: (SessionId) -> PaneDescription = this.describe
    ) = LayoutSaving.build(name, tree, describe, existing, replacing)

    private fun layout(id: Long, name: String) = Layout(id, name, LayoutNode.Pane())

    @Test
    fun theShapeTheProfilesAndTheCommandsAreSaved() {
        val saved = (build("  ops ") as Outcome.Success).value

        assertEquals("ops", saved.name)
        assertEquals(
            LayoutNode.Split(
                SplitOrientation.VERTICAL,
                0.4f,
                LayoutNode.Pane(profileId = 3, command = "top"),
                LayoutNode.Pane()
            ),
            saved.root
        )
    }

    @Test
    fun aBlankCommandIsStoredAsNone() {
        val saved = (build("x", describe = { PaneDescription(command = "  ") }) as Outcome.Success)

        assertEquals(LayoutNode.Pane(), (saved.value.root as LayoutNode.Split).first)
    }

    @Test
    fun aNameIsCheckedLikeAnyOtherAndMustBeFree() {
        assertEquals(Outcome.Failure(DomainError.InvalidName(" ")), build(" "))
        assertEquals(
            Outcome.Failure(DomainError.NameTaken("OPS")),
            build("OPS", existing = listOf(layout(5, "ops")))
        )
    }

    @Test
    fun savingOverALayoutByItsOwnNameWorksButNotByAnothersName() {
        val existing = listOf(layout(5, "ops"), layout(6, "dev"))

        assertEquals(5L, (build("ops", existing, replacing = 5) as Outcome.Success).value.id)
        assertEquals(
            Outcome.Failure(DomainError.NameTaken("dev")),
            build("dev", existing, replacing = 5)
        )
    }

    @Test
    fun thereIsALimitOnHowManyLayoutsAreKept() {
        val full = (1L..LayoutSaving.MAX_LAYOUTS).map { layout(it, "l$it") }

        assertEquals(Outcome.Failure(DomainError.InvalidValue("layouts")), build("new", full))
        // Replacing one does not add another.
        assertEquals(1L, (build("l1", full, replacing = 1) as Outcome.Success).value.id)
    }

    @Test
    fun aCommandThatIsNotOnePlainLineIsRefusedNow() {
        val result = build("x", describe = { PaneDescription(command = "a\nb") })

        assertEquals(Outcome.Failure(DomainError.InvalidValue("command")), result)
    }
}
