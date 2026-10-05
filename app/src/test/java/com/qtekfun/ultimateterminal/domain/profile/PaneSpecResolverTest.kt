// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.session.distro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaneSpecResolverTest {
    private val alpine = distro(1, "Alpine", isDefault = true)
    private val debian = distro(2, "Debian")
    private val busy = distro(3, "Busy", state = DistroState.INSTALLING)
    private val resolver = PaneSpecResolver(
        distros = listOf(alpine, debian, busy),
        knownSchemeIds = setOf("nord"),
        knownFontIds = setOf("fira")
    )

    private fun ready(resolution: PaneResolution) = resolution as PaneResolution.Ready

    private fun rejected(resolution: PaneResolution) =
        (resolution as PaneResolution.Rejected).problem

    @Test
    fun noProfileOpensTheDefaultDistroWithTheGlobalLook() {
        val result = ready(resolver.resolve(null))

        assertEquals(PaneTarget.InDistro(1, "Alpine", null), result.spec.target)
        assertEquals(PaneLook(scrollbackLines = Profile.DEFAULT_SCROLLBACK), result.spec.look)
        assertNull(result.spec.startupInput)
        assertNull(result.spec.profileId)
        assertTrue(result.notices.isEmpty())
    }

    @Test
    fun withoutAReadyDefaultDistroThePaneOpensInAndroidsShellWithANotice() {
        val none = PaneSpecResolver(emptyList(), emptySet(), emptySet())
        val installing = PaneSpecResolver(
            listOf(distro(1, "Alpine", DistroState.INSTALLING, isDefault = true)),
            emptySet(),
            emptySet()
        )

        for (resolver in listOf(none, installing)) {
            val result = ready(resolver.resolve(null))
            assertEquals(PaneTarget.AndroidShell, result.spec.target)
            assertEquals(listOf(PaneNotice.NoDistroReady), result.notices)
        }
    }

    @Test
    fun aProfileNamesItsDistroAndItsUser() {
        val profile = Profile(id = 5, name = "dev", distroId = 2, user = "dev")

        val result = ready(resolver.resolve(profile))

        assertEquals(PaneTarget.InDistro(2, "Debian", "dev"), result.spec.target)
        assertEquals(5L, result.spec.profileId)
        assertEquals("dev", result.spec.profileName)
    }

    @Test
    fun aProfileThatWasNeverSavedHasNoId() {
        val spec = ready(resolver.resolve(Profile(name = "draft"))).spec
        assertNull(spec.profileId)
        assertNull(spec.profileName)
    }

    @Test
    fun aDistroThatIsGoneOrNotReadyIsRefusedWithTheReason() {
        assertEquals(
            ProfileProblem.UnknownDistro(9),
            rejected(resolver.resolve(Profile(name = "x", distroId = 9)))
        )
        assertEquals(
            ProfileProblem.DistroNotReady("Busy"),
            rejected(resolver.resolve(Profile(name = "x", distroId = 3)))
        )
    }

    @Test
    fun aUserThatSuCouldReadAsAnOptionIsRefusedInADistroButIgnoredInAndroidsShell() {
        assertEquals(
            ProfileProblem.InvalidUser("-x"),
            rejected(resolver.resolve(Profile(name = "x", distroId = 2, user = "-x")))
        )
        assertEquals(
            ProfileProblem.InvalidUser(""),
            rejected(resolver.resolve(Profile(name = "x", user = "")))
        )
        val android = PaneSpecResolver(emptyList(), emptySet(), emptySet())

        assertEquals(
            PaneTarget.AndroidShell,
            ready(android.resolve(Profile(name = "x", user = "-x"))).spec.target
        )
    }

    @Test
    fun theStoredDefaultsMeanTheGlobalSettingAndOnlyOtherValuesOverrideIt() {
        val plain = ready(resolver.resolve(Profile(name = "plain"))).spec.look
        val custom = ready(
            resolver.resolve(
                Profile(
                    name = "custom",
                    colorSchemeId = "nord",
                    fontFamily = "fira",
                    fontSizeSp = 20,
                    scrollbackLines = 5_000
                )
            )
        ).spec.look

        assertEquals(PaneLook(null, null, null, Profile.DEFAULT_SCROLLBACK), plain)
        assertEquals(PaneLook("nord", "fira", 20, 5_000), custom)
    }

    @Test
    fun aSchemeOrFontThatIsGoneFallsBackToTheGlobalOneWithANotice() {
        val result = ready(
            resolver.resolve(Profile(name = "x", colorSchemeId = "gone", fontFamily = "lost"))
        )

        assertNull(result.spec.look.colorSchemeId)
        assertNull(result.spec.look.fontId)
        assertEquals(
            listOf(PaneNotice.SchemeMissing("gone"), PaneNotice.FontMissing("lost")),
            result.notices
        )
    }

    @Test
    fun numbersOutsideTheirRangeAreBroughtInsideWithANotice() {
        val result = ready(
            resolver.resolve(Profile(name = "x", fontSizeSp = 200, scrollbackLines = 5))
        )

        assertEquals(Profile.FONT_SIZE_RANGE.last, result.spec.look.fontSizeSp)
        assertEquals(Profile.SCROLLBACK_RANGE.first, result.spec.look.scrollbackLines)
        assertEquals(
            listOf(
                PaneNotice.ValueAdjusted("fontSizeSp"),
                PaneNotice.ValueAdjusted("scrollbackLines")
            ),
            result.notices
        )
    }

    @Test
    fun theStartupCommandIsTypedFollowedByEnterAndACommandOfThePaneReplacesIt() {
        val profile = Profile(name = "x", startupCommand = "htop")

        assertEquals("htop\r", ready(resolver.resolve(profile)).spec.startupInput)
        assertEquals("ls -l\r", ready(resolver.resolve(profile, "  ls -l ")).spec.startupInput)
    }

    @Test
    fun aBlankCommandOfThePaneCancelsTheProfilesOne() {
        val profile = Profile(name = "x", startupCommand = "htop")

        assertNull(ready(resolver.resolve(profile, "   ")).spec.startupInput)
    }

    @Test
    fun aBlankStartupCommandMeansNoCommand() {
        assertNull(
            ready(resolver.resolve(Profile(name = "x", startupCommand = "  "))).spec.startupInput
        )
    }

    @Test
    fun aStartupCommandThatIsNotOnePlainLineIsRefused() {
        fun fault(command: String) = rejected(resolver.resolve(null, command))

        assertEquals(
            ProfileProblem.InvalidStartupCommand(StartupCommandFault.MULTIPLE_LINES),
            fault("a\nb")
        )
        assertEquals(
            ProfileProblem.InvalidStartupCommand(StartupCommandFault.CONTROL_CHARACTER),
            fault("a\tb")
        )
        assertEquals(
            ProfileProblem.InvalidStartupCommand(StartupCommandFault.TOO_LONG),
            fault("x".repeat(StartupCommand.MAX_LENGTH + 1))
        )
    }

    @Test
    fun degradingOpensThePaneAsTheDefaultOneAndSaysWhy() {
        val missing = Profile(name = "x", distroId = 9, startupCommand = "top")

        val result = resolver.resolveOrDegrade(missing)

        assertEquals(PaneTarget.InDistro(1, "Alpine", null), result.spec.target)
        assertNull(result.spec.startupInput)
        assertEquals(listOf(PaneNotice.Degraded(ProfileProblem.UnknownDistro(9))), result.notices)
    }

    @Test
    fun degradingLeavesAPaneThatCanOpenAsItIs() {
        val profile = Profile(id = 4, name = "x", distroId = 2, startupCommand = "top")

        val result = resolver.resolveOrDegrade(profile)

        assertEquals(PaneTarget.InDistro(2, "Debian", null), result.spec.target)
        assertEquals("top\r", result.spec.startupInput)
        assertTrue(result.notices.isEmpty())
    }
}
