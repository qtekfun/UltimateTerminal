// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

/** Where a pane runs: Android's own shell, or a distro under proot. */
sealed interface PaneTarget {
    data object AndroidShell : PaneTarget

    /** [user] is null when the distro's own default user applies. */
    data class InDistro(val distroId: Long, val distroName: String, val user: String?) :
        PaneTarget
}

/**
 * How one pane looks when it differs from the global appearance. A null field means "the global
 * setting": the profile did not customize it, so changing the global scheme or font still reaches
 * the pane. [scrollbackLines] is always given, because it has no global counterpart per pane.
 */
data class PaneLook(
    val colorSchemeId: String? = null,
    val fontId: String? = null,
    val fontSizeSp: Int? = null,
    val scrollbackLines: Int
)

/**
 * Everything needed to open one pane, decided before any process exists. The session itself is
 * built from [target] by the launch code that already exists (T08b, T14); this carries what the
 * profile adds to it.
 *
 * [startupInput] is typed into the shell once it is running, ending with Enter (`\r`), and is not
 * executed instead of the shell: the shell stays interactive, nothing needs quoting, and a
 * command that fails leaves a prompt to fix it.
 */
data class PaneSpec(
    val target: PaneTarget,
    val look: PaneLook,
    val startupInput: String?,
    /** The profile this came from, null when no profile was used. */
    val profileId: Long? = null,
    /** The name of that profile, which names the tab it opens (D-FIX-4). */
    val profileName: String? = null
)

/**
 * What a pane was opened with: its [spec] and the [command] its saved layout gave it (null if the
 * command, if any, is the profile's). The session owner remembers it for as long as the pane
 * lives, so the factory can start the shell as the profile says and a tab can be saved as a layout.
 */
data class PaneOpening(val spec: PaneSpec, val command: String? = null) {
    /** The distro to open in; null for Android's own shell. */
    val distroId: Long? get() = (spec.target as? PaneTarget.InDistro)?.distroId

    /**
     * What a pane made by splitting this one starts with (D-FIX-8): the same target, user and look,
     * but not the start-up command, which would run a second time.
     */
    fun forSplit(): PaneOpening = PaneOpening(spec.copy(startupInput = null))
}

/** Why a pane could not be opened as asked. A notice, by contrast, only says what was changed. */
sealed interface ProfileProblem {
    /** The profile names a distro that is not installed. */
    data class UnknownDistro(val distroId: Long) : ProfileProblem

    /** The distro exists but is not usable (still installing, or failed). */
    data class DistroNotReady(val distroName: String) : ProfileProblem

    /** A user name that `su` could read as an option or that no distro accepts. */
    data class InvalidUser(val user: String) : ProfileProblem

    /** A start-up command that is not a single plain line. */
    data class InvalidStartupCommand(val reason: StartupCommandFault) : ProfileProblem
}

/** What is wrong with a start-up command. */
enum class StartupCommandFault { EMPTY, TOO_LONG, MULTIPLE_LINES, CONTROL_CHARACTER }

/** Something the opening changed so the pane could open anyway. */
sealed interface PaneNotice {
    /** The profile no longer exists (it was deleted); the default one is used. */
    data class ProfileMissing(val profileId: Long) : PaneNotice

    /** The profile's color scheme is gone (an imported one was removed); the global one is used. */
    data class SchemeMissing(val schemeId: String) : PaneNotice

    /** The profile's font is gone (an imported one was removed); the global one is used. */
    data class FontMissing(val fontId: String) : PaneNotice

    /** A stored number was outside its range and was brought into it. */
    data class ValueAdjusted(val field: String) : PaneNotice

    /** No distro is ready, so the pane opens in Android's own shell. */
    data object NoDistroReady : PaneNotice

    /** The pane could not open as saved and opens plainly instead; [problem] says why. */
    data class Degraded(val problem: ProfileProblem) : PaneNotice
}

/** The outcome of working out a pane: it opens (maybe with notices) or it is refused. */
sealed interface PaneResolution {
    data class Ready(val spec: PaneSpec, val notices: List<PaneNotice> = emptyList()) :
        PaneResolution

    data class Rejected(val problem: ProfileProblem) : PaneResolution
}
