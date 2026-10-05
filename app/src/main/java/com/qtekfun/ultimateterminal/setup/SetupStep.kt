// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.setup

import com.qtekfun.ultimateterminal.distro.DistroMessage
import com.qtekfun.ultimateterminal.distro.DistroUiState
import com.qtekfun.ultimateterminal.distro.InstallUiState
import com.qtekfun.ultimateterminal.domain.distro.InstallError

/**
 * Where the setup flow is, derived from the install state of [com.qtekfun.ultimateterminal.distro.DistroViewModel]
 * so the installer is not duplicated. There is no dead end: a failed install is a [Choose] step
 * that remembers the [lastError], so the form is still there to retry or to pick another distro.
 */
sealed interface SetupStep {
    data class Choose(val lastError: InstallError? = null) : SetupStep

    data class Installing(val install: InstallUiState) : SetupStep
}

fun setupStepOf(state: DistroUiState): SetupStep {
    val install = state.installing
    if (install != null) return SetupStep.Installing(install)
    val failure = (state.message as? DistroMessage.InstallFailed)?.error
    return SetupStep.Choose(failure)
}
