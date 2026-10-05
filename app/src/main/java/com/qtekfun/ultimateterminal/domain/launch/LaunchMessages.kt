// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

import com.qtekfun.ultimateterminal.domain.storage.StorageDegradation

/** Why a tab could not start what it was asked to. The UI shows a message and never crashes. */
sealed interface LaunchProblem {
    /** `libproot.so` or its loader is not in the app's native library directory. */
    data object ProotMissing : LaunchProblem

    /** The distro is registered as ready but its root filesystem is not on disk. */
    data class DistroCorrupt(val distroName: String) : LaunchProblem

    /** The distro's default user is not a valid user name. */
    data class InvalidUser(val user: String) : LaunchProblem

    /**
     * The distro's default user is not in its `/etc/passwd` and could not be created (or the file
     * could not be read), so there is nobody to run the shell as.
     */
    data class UserUnavailable(val user: String) : LaunchProblem

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
