// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.launch.LaunchNotice
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem

/** What a new tab should actually start, decided before any process exists. */
sealed interface LaunchPlan {
    /** The user chose the Android shell. */
    data object AndroidShell : LaunchPlan

    /** The distro asked for cannot be used, so the Android shell stands in, with a notice. */
    data class FallbackToAndroid(val notice: LaunchNotice) : LaunchPlan

    /** Run [launch] (proot with the distro). [notice] says what could not be offered, if anything. */
    data class InDistro(
        val launch: ProotLaunch,
        val distroName: String,
        val notice: LaunchNotice? = null
    ) : LaunchPlan

    /** Nothing may start; the UI explains why. */
    data class Failed(val problem: LaunchProblem) : LaunchPlan
}
