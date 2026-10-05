// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.Validation

/**
 * What the profile form holds while it is being typed: every field is text, because that is what a
 * text box has, and it is checked when the user saves ([ProfileForm.build]).
 */
data class ProfileDraft(
    val name: String = "",
    /** The distro to open; null is the default distro. */
    val distroId: Long? = null,
    val user: String = "",
    val scrollback: String = Profile.DEFAULT_SCROLLBACK.toString(),
    val startupCommand: String = ""
)

/** The fields of the profile form that can be wrong. */
enum class ProfileField { NAME, USER, SCROLLBACK, STARTUP_COMMAND }

sealed interface ProfileFormResult {
    data class Valid(val profile: Profile) : ProfileFormResult

    /** Every field that is wrong, so the form can mark them all at once. */
    data class Invalid(val fields: Set<ProfileField>) : ProfileFormResult
}

/**
 * The profile form (SPEC RF-12). A profile keeps what the form does not edit (color scheme, font
 * and size, D-T12b-9) from the profile being edited, so editing never resets it.
 */
object ProfileForm {
    fun draftOf(profile: Profile?): ProfileDraft = if (profile == null) {
        ProfileDraft()
    } else {
        ProfileDraft(
            name = profile.name,
            distroId = profile.distroId,
            user = profile.user.orEmpty(),
            scrollback = profile.scrollbackLines.toString(),
            startupCommand = profile.startupCommand.orEmpty()
        )
    }

    /** The profile [draft] describes, on top of [base] (null for a new one), or what is wrong. */
    fun build(draft: ProfileDraft, base: Profile? = null): ProfileFormResult {
        val name = Validation.name(draft.name)
        val user = draft.user.trim().ifEmpty { null }
        val scrollback = draft.scrollback.trim().toIntOrNull()
        val command = StartupCommand.check(draft.startupCommand)
        val wrong = buildSet {
            if (name is Outcome.Failure) add(ProfileField.NAME)
            if (user != null && !GuestUser.isValid(user)) add(ProfileField.USER)
            if (scrollback == null || scrollback !in Profile.SCROLLBACK_RANGE) {
                add(ProfileField.SCROLLBACK)
            }
            if (command is StartupCommand.Check.Invalid) add(ProfileField.STARTUP_COMMAND)
        }
        return if (wrong.isNotEmpty()) {
            ProfileFormResult.Invalid(wrong)
        } else {
            ProfileFormResult.Valid(
                (base ?: Profile(name = "")).copy(
                    name = (name as Outcome.Success).value,
                    distroId = draft.distroId,
                    user = user,
                    scrollbackLines = requireNotNull(scrollback),
                    startupCommand = (command as StartupCommand.Check.Valid).text
                )
            )
        }
    }
}
