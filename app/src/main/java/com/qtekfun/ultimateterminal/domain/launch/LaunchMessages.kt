// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

import com.qtekfun.ultimateterminal.domain.storage.StorageDegradation

/** What a new tab was asked to run: a distro (null for the Android shell) and, optionally, a command. */
data class ShellRequest(
    val distroId: Long?,
    /**
     * A program and its arguments to run instead of the login shell (SSH hosts use it). It is an
     * argument list, never a shell line, so nothing in it is interpreted by a shell.
     */
    val initialCommand: List<String>? = null
)

/** Why a tab could not start what it was asked to. The UI shows a message and never crashes. */
sealed interface LaunchProblem {
    /** `libproot.so` or its loader is not in the app's native library directory. */
    data object ProotMissing : LaunchProblem

    /** The distro is registered as ready but its root filesystem is not on disk. */
    data class DistroCorrupt(val distroName: String) : LaunchProblem

    /** The distro's default user is not a valid user name. */
    data class InvalidUser(val user: String) : LaunchProblem

    /** A command needs a distro, but the tab has none or it is not ready; no shell may stand in. */
    data class DistroUnavailable(val distroName: String?) : LaunchProblem

    /** A command was given with no distro at all. */
    data object CommandNeedsDistro : LaunchProblem

    /** The command is empty or contains a NUL character. */
    data object InvalidCommand : LaunchProblem

    /** proot has nowhere private to put its temporary files. */
    data object TempDirUnavailable : LaunchProblem

    /** Something failed that the app did not foresee; the shell did not start. */
    data object Unexpected : LaunchProblem
}

/** Something the user should know about a shell that did start. */
sealed interface LaunchNotice {
    /** The distro asked for is gone or not ready, so the Android shell opened instead. */
    data class DistroUnavailable(val distroName: String?) : LaunchNotice

    /** The user turned the shared storage on but it could not be mounted. */
    data class StorageNotMounted(val reason: StorageDegradation) : LaunchNotice

    /** The distro has no `/etc/resolv.conf` from the app, so names may not resolve. */
    data object DnsNotConfigured : LaunchNotice
}

/** What the screen tells the user about the active tab's start: a problem, or a notice. */
sealed interface LaunchMessage {
    data class Problem(val problem: LaunchProblem) : LaunchMessage

    data class Notice(val notice: LaunchNotice) : LaunchMessage
}
