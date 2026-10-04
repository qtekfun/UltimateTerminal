// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.Profile

/**
 * Turns a [Profile] into the [PaneSpec] of a pane (SPEC RF-12). Pure: it is given what exists
 * (the installed distros, the known color schemes and fonts) and returns a value, never throwing
 * for a problem a user can have: stored data may be old, edited or from another device.
 *
 * A [Profile] keeps its stored defaults (`default`, `monospace`, size 14) for "not customized", so
 * those mean "use the global setting" and only a different value overrides it.
 */
class PaneSpecResolver(
    private val distros: List<Distro>,
    private val knownSchemeIds: Set<String>,
    private val knownFontIds: Set<String>,
    private val defaultScrollbackLines: Int = Profile.DEFAULT_SCROLLBACK
) {
    /**
     * The pane for [profile] (null: no profile, the global settings and the default distro).
     * [command] replaces the profile's start-up command, which is how a saved layout gives each
     * pane its own.
     */
    fun resolve(profile: Profile?, command: String? = null): PaneResolution =
        when (val target = targetOf(profile)) {
            is TargetResult.Failed -> PaneResolution.Rejected(target.problem)
            is TargetResult.Chosen -> resolveIn(profile, command, target)
        }

    private fun resolveIn(
        profile: Profile?,
        command: String?,
        target: TargetResult.Chosen
    ): PaneResolution {
        val problem = userProblem(profile, target)
        val startup = startupOf(command ?: profile?.startupCommand)
        return when {
            problem != null -> PaneResolution.Rejected(problem)

            startup is StartupCommand.Check.Invalid ->
                PaneResolution.Rejected(ProfileProblem.InvalidStartupCommand(startup.fault))

            else -> ready(profile, target, (startup as StartupCommand.Check.Valid).text)
        }
    }

    /**
     * Like [resolve], but a pane that cannot open as asked opens plainly (default profile, no
     * start-up command) with a [PaneNotice.Degraded]: a saved layout must open whatever has changed
     * since it was saved.
     */
    fun resolveOrDegrade(profile: Profile?, command: String? = null): PaneResolution.Ready =
        when (val first = resolve(profile, command)) {
            is PaneResolution.Ready -> first

            is PaneResolution.Rejected -> {
                val plain = resolve(null, null) as PaneResolution.Ready
                plain.copy(notices = plain.notices + PaneNotice.Degraded(first.problem))
            }
        }

    private sealed interface TargetResult {
        data class Chosen(val target: PaneTarget, val notices: List<PaneNotice>) : TargetResult

        data class Failed(val problem: ProfileProblem) : TargetResult
    }

    private fun targetOf(profile: Profile?): TargetResult {
        val wanted = profile?.distroId
        val distro = if (wanted == null) {
            distros.firstOrNull { it.isDefault && it.state == DistroState.READY }
        } else {
            distros.firstOrNull { it.id == wanted }
        }
        return when {
            wanted != null && distro == null ->
                TargetResult.Failed(ProfileProblem.UnknownDistro(wanted))

            distro == null ->
                TargetResult.Chosen(PaneTarget.AndroidShell, listOf(PaneNotice.NoDistroReady))

            distro.state != DistroState.READY ->
                TargetResult.Failed(ProfileProblem.DistroNotReady(distro.name))

            else -> TargetResult.Chosen(
                PaneTarget.InDistro(distro.id, distro.name, profile?.user),
                emptyList()
            )
        }
    }

    /** A user only matters inside a distro; Android's shell has no such choice. */
    private fun userProblem(profile: Profile?, target: TargetResult.Chosen): ProfileProblem? {
        val user = profile?.user
        val inDistro = target.target is PaneTarget.InDistro
        return if (inDistro && user != null && !GuestUser.isValid(user)) {
            ProfileProblem.InvalidUser(user)
        } else {
            null
        }
    }

    private fun startupOf(raw: String?): StartupCommand.Check = StartupCommand.check(raw)

    private fun ready(
        profile: Profile?,
        chosen: TargetResult.Chosen,
        command: String?
    ): PaneResolution {
        val notices = chosen.notices.toMutableList()
        val look = if (profile == null) {
            PaneLook(scrollbackLines = defaultScrollbackLines)
        } else {
            lookOf(profile, notices)
        }
        return PaneResolution.Ready(
            PaneSpec(
                chosen.target,
                look,
                command?.let(StartupCommand::inputFor),
                profile?.id?.takeIf { it != 0L }
            ),
            notices
        )
    }

    private fun lookOf(profile: Profile, notices: MutableList<PaneNotice>): PaneLook {
        val scheme = profile.colorSchemeId.takeUnless { it == Profile.DEFAULT_COLOR_SCHEME }
            ?.let { known(it, knownSchemeIds, notices, PaneNotice::SchemeMissing) }
        val font = profile.fontFamily.takeUnless { it == Profile.DEFAULT_FONT }
            ?.let { known(it, knownFontIds, notices, PaneNotice::FontMissing) }
        val size = profile.fontSizeSp.takeUnless { it == Profile.DEFAULT_FONT_SIZE }
            ?.let { clamped(it, Profile.FONT_SIZE_RANGE, "fontSizeSp", notices) }
        val scrollback =
            clamped(profile.scrollbackLines, Profile.SCROLLBACK_RANGE, "scrollbackLines", notices)
        return PaneLook(scheme, font, size, scrollback)
    }

    /** [id] if it is one of [ids]; otherwise null and a notice saying it is gone. */
    private fun known(
        id: String,
        ids: Set<String>,
        notices: MutableList<PaneNotice>,
        missing: (String) -> PaneNotice
    ): String? {
        if (id !in ids) notices += missing(id)
        return id.takeIf { it in ids }
    }

    private fun clamped(
        value: Int,
        range: IntRange,
        field: String,
        notices: MutableList<PaneNotice>
    ): Int {
        val inside = value.coerceIn(range)
        if (inside != value) notices += PaneNotice.ValueAdjusted(field)
        return inside
    }
}
