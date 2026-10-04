// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain

import com.qtekfun.ultimateterminal.data.local.LayoutCodec
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.model.paneCount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainModelTest {
    private fun isFailure(outcome: Outcome<*>) = outcome is Outcome.Failure

    @Test
    fun `outcome helpers pass values and failures along`() {
        val ok: Outcome<Int> = Outcome.Success(2)
        val bad: Outcome<Int> = Outcome.Failure(DomainError.NotFound)

        assertEquals(2, ok.getOrNull())
        assertNull(bad.getOrNull())
        assertEquals(Outcome.Success(4), ok.map { it * 2 })
        assertEquals(bad, bad.map { it * 2 })
        assertEquals(Outcome.Success("2"), ok.flatMap { Outcome.Success(it.toString()) })
        assertEquals(bad, bad.flatMap { Outcome.Success(it.toString()) })
    }

    @Test
    fun `names are trimmed and limited`() {
        assertEquals(Outcome.Success("a b"), Validation.name("  a b "))
        assertEquals(Outcome.Success("a".repeat(64)), Validation.name("a".repeat(64)))
        assertTrue(isFailure(Validation.name("a".repeat(65))))
        assertTrue(isFailure(Validation.name("")))
        assertTrue(isFailure(Validation.name("tab\there")))
        assertTrue(isFailure(Validation.name("\u0000")))
    }

    @Test
    fun `paths cannot leave the storage root`() {
        assertEquals("distros/a", FsPath.of("distros/a").getOrNull()?.value)
        val unsafe =
            listOf("", "/etc", "a//b", "a/", "./a", "a/./b", "../a", "a/../b", "a/..", "a\\b")
        (unsafe + "a\u0000b").forEach {
            assertEquals(Outcome.Failure(DomainError.InvalidPath(it)), FsPath.of(it), "path '$it'")
        }
    }

    @Test
    fun `a child path is a plain name inside its parent`() {
        val parent = checkNotNull(FsPath.of("distros/a").getOrNull())

        assertEquals("distros/a/rootfs", parent.child("rootfs").getOrNull()?.toString())
        assertTrue(isFailure(parent.child("..")))
        assertTrue(isFailure(parent.child("x/../../y")))
        assertTrue(isFailure(parent.child("")))
    }

    @Test
    fun `a split needs a ratio strictly between 0 and 1`() {
        val pane = LayoutNode.Pane()

        listOf(0f, 1f, -0.5f, 1.5f).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                LayoutNode.Split(SplitOrientation.HORIZONTAL, it, pane, pane)
            }
        }
        LayoutNode.Split(SplitOrientation.HORIZONTAL, 0.5f, pane, pane)
    }

    @Test
    fun `panes are counted through the tree`() {
        val tree = LayoutNode.Split(
            SplitOrientation.VERTICAL,
            0.5f,
            LayoutNode.Pane(),
            LayoutNode.Split(
                SplitOrientation.HORIZONTAL,
                0.3f,
                LayoutNode.Pane(),
                LayoutNode.Pane()
            )
        )

        assertEquals(1, LayoutNode.Pane().paneCount())
        assertEquals(3, tree.paneCount())
    }

    @Test
    fun `the stored layout form round trips and tolerates unknown keys`() {
        val tree = LayoutNode.Split(
            SplitOrientation.VERTICAL,
            0.25f,
            LayoutNode.Pane(profileId = 1L, command = "top"),
            LayoutNode.Pane()
        )

        assertEquals(tree, LayoutCodec.decode(LayoutCodec.encode(tree)))
        assertEquals(
            LayoutNode.Pane(command = "ls"),
            LayoutCodec.decode("""{"type":"pane","command":"ls","fromTheFuture":true}""")
        )
        assertNull(LayoutCodec.decode("""{"type":"unknown"}"""))
        assertNull(LayoutCodec.decode(""))
    }

    @Test
    fun `hosts, users and ports are checked for ssh`() {
        assertEquals(Outcome.Success("srv.example.com"), Validation.host(" srv.example.com "))
        assertTrue(isFailure(Validation.host("-x")))
        assertTrue(isFailure(Validation.host("a b")))
        assertEquals(Outcome.Success("ana"), Validation.user("ana"))
        assertEquals(Outcome.Success(".hidden-1"), Validation.user(".hidden-1"))
        assertTrue(isFailure(Validation.user("-ana")))
        assertTrue(isFailure(Validation.user("")))
        assertEquals(Outcome.Success(22), Validation.port(22))
        assertTrue(isFailure(Validation.port(0)))
        assertTrue(isFailure(Validation.port(70_000)))
    }
}
