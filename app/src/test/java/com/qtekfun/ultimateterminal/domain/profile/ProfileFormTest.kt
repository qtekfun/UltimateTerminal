// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.model.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfileFormTest {
    private fun valid(draft: ProfileDraft, base: Profile? = null) =
        (ProfileForm.build(draft, base) as ProfileFormResult.Valid).profile

    private fun wrong(draft: ProfileDraft) =
        (ProfileForm.build(draft) as ProfileFormResult.Invalid).fields

    @Test
    fun aNewProfileStartsWithTheDefaults() {
        val draft = ProfileForm.draftOf(null)

        assertEquals(ProfileDraft(), draft)
        assertEquals(setOf(ProfileField.NAME), wrong(draft))
    }

    @Test
    fun aValidDraftBecomesAProfileWithTrimmedValues() {
        val profile = valid(
            ProfileDraft(
                name = "  Work ",
                distroId = 4,
                user = " dev ",
                scrollback = " 2000 ",
                startupCommand = "  tmux attach "
            )
        )

        assertEquals(
            Profile(
                name = "Work",
                distroId = 4,
                user = "dev",
                scrollbackLines = 2000,
                startupCommand = "tmux attach"
            ),
            profile
        )
    }

    @Test
    fun blankUserAndCommandMeanNone() {
        val profile = valid(ProfileDraft(name = "x", user = "  ", startupCommand = " "))

        assertEquals(null, profile.user)
        assertEquals(null, profile.startupCommand)
    }

    @Test
    fun editingKeepsWhatTheFormDoesNotShow() {
        val existing = Profile(
            id = 9,
            name = "Old",
            colorSchemeId = "nord",
            fontFamily = "fira",
            fontSizeSp = 20
        )

        val edited = valid(ProfileForm.draftOf(existing).copy(name = "New"), existing)

        assertEquals(existing.copy(name = "New"), edited)
    }

    @Test
    fun theDraftOfAProfileShowsItsFields() {
        val profile = Profile(
            name = "p",
            distroId = 2,
            user = "u",
            scrollbackLines = 500,
            startupCommand = "top"
        )

        assertEquals(ProfileDraft("p", 2, "u", "500", "top"), ProfileForm.draftOf(profile))
    }

    @Test
    fun everyWrongFieldIsReportedAtOnce() {
        val fields = wrong(
            ProfileDraft(
                name = " ",
                user = "-root",
                scrollback = "12",
                startupCommand = "a\nb"
            )
        )

        assertEquals(
            setOf(
                ProfileField.NAME,
                ProfileField.USER,
                ProfileField.SCROLLBACK,
                ProfileField.STARTUP_COMMAND
            ),
            fields
        )
    }

    @Test
    fun theScrollbackMustBeANumberInRange() {
        for (bad in listOf("", "many", "99", "1000001")) {
            assertEquals(
                setOf(ProfileField.SCROLLBACK),
                wrong(ProfileDraft(name = "x", scrollback = bad))
            )
        }
        assertEquals(100, valid(ProfileDraft(name = "x", scrollback = "100")).scrollbackLines)
        assertEquals(
            1_000_000,
            valid(ProfileDraft(name = "x", scrollback = "1000000")).scrollbackLines
        )
    }
}
