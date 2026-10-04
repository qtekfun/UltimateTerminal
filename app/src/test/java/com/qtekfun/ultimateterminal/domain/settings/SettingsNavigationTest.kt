// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsNavigationTest {
    @Test
    fun startsAtTheRootAndCannotGoBackFromIt() {
        val start = SettingsNavigation()

        assertEquals(SettingsPage.ROOT, start.current)
        assertFalse(start.canGoBack)
        assertSame(start, start.back())
    }

    @Test
    fun opensPagesOnTopAndGoesBackOneAtATime() {
        val deep = SettingsNavigation().open(SettingsPage.KEYBOARD).open(SettingsPage.KEYBOARD_KEYS)

        assertEquals(SettingsPage.KEYBOARD_KEYS, deep.current)
        assertTrue(deep.canGoBack)
        assertEquals(SettingsPage.KEYBOARD, deep.back().current)
        assertEquals(SettingsPage.ROOT, deep.back().back().current)
    }

    @Test
    fun openingThePageAlreadyShownChangesNothing() {
        val shown = SettingsNavigation().open(SettingsPage.NETWORK)

        assertSame(shown, shown.open(SettingsPage.NETWORK))
    }

    @Test
    fun openingTheRootGoesBackToIt() {
        val back = SettingsNavigation().open(SettingsPage.ABOUT).open(SettingsPage.NOTICES)
            .open(SettingsPage.ROOT)

        assertEquals(SettingsNavigation(), back)
    }

    @Test
    fun theStackSurvivesBeingSavedAndRestored() {
        val nav = SettingsNavigation().open(SettingsPage.ABOUT).open(SettingsPage.NOTICES)

        assertEquals(nav, SettingsNavigation.fromNames(nav.toNames()))
    }

    @Test
    fun savedNamesThatMakeNoSenseGiveTheRoot() {
        assertEquals(SettingsNavigation(), SettingsNavigation.fromNames(emptyList()))
        assertEquals(SettingsNavigation(), SettingsNavigation.fromNames(listOf("NOPE")))
        assertEquals(SettingsNavigation(), SettingsNavigation.fromNames(listOf("ABOUT", "NOTICES")))
        assertEquals(
            SettingsNavigation().open(SettingsPage.ABOUT),
            SettingsNavigation.fromNames(listOf("ROOT", "BOGUS", "ABOUT"))
        )
    }

    @Test
    fun theBackButtonOfAPageNamesItsParent() {
        assertEquals(SettingsPage.KEYBOARD, SettingsPage.KEYBOARD_KEYS.parent)
        assertEquals(SettingsPage.KEYBOARD, SettingsPage.SHORTCUTS.parent)
        assertEquals(SettingsPage.ABOUT, SettingsPage.NOTICES.parent)
        assertEquals(SettingsPage.ROOT, SettingsPage.NETWORK.parent)
        assertEquals(SettingsPage.ROOT, SettingsPage.ROOT.parent)
    }

    @Test
    fun everySubPageHasAParentThatIsOneStepUp() {
        for (page in SettingsPage.entries.filter { it != SettingsPage.ROOT }) {
            val path = SettingsNavigation().open(page.parent).open(page)

            assertEquals(page, path.current)
            assertEquals(page.parent, path.back().current)
        }
    }
}
