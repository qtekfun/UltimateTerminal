// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The project has no Compose UI tests, so this guards by source the bug found on a Pixel 8: after
 * the sidebar collapsed, keys did not reach the terminal and a space re-opened the bar and closed the
 * keyboard, because a focusable control of the bar had the keyboard focus (D-T26-7).
 */
class SidebarFocusGuardTest {
    private val root = File("src/main/java/com/qtekfun/ultimateterminal")
    private fun text(path: String) = File(root, path).readText()

    @Test
    fun `the tab bar takes no keyboard focus`() {
        assertTrue("canFocus = false" in text("ui/TabBar.kt"))
    }

    @Test
    fun `the sidebar chevron is not a focus target`() {
        val rail = text("ui/TabRail.kt")
        assertFalse(".clickable(" in rail)
        assertFalse(".combinedClickable(" in rail)
        assertTrue("onClick(label" in rail)
    }

    @Test
    fun `the more options button opens the menu without being a focus target`() {
        val bar = text("ui/TabBar.kt")
        val button = bar.substringAfter("private fun MoreOptionsButton(")
        assertTrue("IosGlyph.ELLIPSIS" in button)
        assertTrue("onClick(label" in button)
        assertTrue("stateDescription" in button)
        assertFalse(".clickable(" in button)
        assertFalse(".combinedClickable(" in button)
        // The long press on "+" stays as a shortcut to the same menu.
        assertTrue("onLongClick = { menuFrom = NewTabMenuSource.PLUS }" in bar)
    }

    @Test
    fun `the sidebar files never hide the keyboard`() {
        listOf("ui/Sidebar.kt", "ui/TabRail.kt", "ui/TabBar.kt").forEach {
            assertFalse("hideKeyboard" in text(it), it)
        }
    }

    @Test
    fun `the screen gives the keyboard back when the sidebar changes`() {
        val screen = text("ui/TerminalScreen.kt")
        assertTrue("LaunchedEffect(sidebar.open)" in screen)
        assertTrue("restoreKeyboard()" in screen)
    }

    @Test
    fun `only the covered screens hide the keyboard`() {
        val users = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { "hideKeyboard()" in it.readText() }.map { it.name }.toSet()
        assertTrue(users == setOf("TerminalInputView.kt", "TerminalScreen.kt"), users.toString())
    }
}
