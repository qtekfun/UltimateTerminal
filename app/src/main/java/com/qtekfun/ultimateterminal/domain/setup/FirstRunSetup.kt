// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.setup

import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily

/** Whether the first-run setup screen is up, still to be decided, or out of the way for good. */
enum class SetupGate { UNDECIDED, SHOWING, CLOSED }

/** What the decision of [FirstRunSetup.next] looks at. */
data class SetupInputs(
    /** False until the stored distros have been read: nothing is decided before that. */
    val loaded: Boolean,
    val distros: List<Distro>,
    /** Some tab is already open (the app was reopened with its sessions alive). */
    val hasSessions: Boolean,
    /** The user chose "use the Android shell for now"; lasts as long as the process. */
    val skipped: Boolean,
    /** A backup is being restored: its distros arrive one by one, and the default comes last. */
    val restoring: Boolean = false
)

/** The rules of the first-run setup (SPEC RF-15): when it shows, and what it offers. */
object FirstRunSetup {
    /** Smallest download first: Alpine is the recommended one. */
    val families: List<DistroFamily> = listOf(
        DistroFamily.ALPINE,
        DistroFamily.DEBIAN,
        DistroFamily.UBUNTU,
        DistroFamily.FEDORA
    )

    val recommended: DistroFamily get() = families.first()

    /**
     * The next state of the gate. It shows once, when the app starts with no distro ready and no
     * tab open, and then stays up while the install runs (an install in progress is part of the
     * screen, not a reason to hide it) until a distro is ready or the user skips it. Once closed
     * it stays closed until the process restarts, so a later change never pops it up over a tab.
     */
    fun next(current: SetupGate, inputs: SetupInputs): SetupGate = when (current) {
        SetupGate.CLOSED -> SetupGate.CLOSED

        SetupGate.UNDECIDED -> when {
            !inputs.loaded -> SetupGate.UNDECIDED
            inputs.skipped || inputs.hasSessions || hasReady(inputs.distros) -> SetupGate.CLOSED
            else -> SetupGate.SHOWING
        }

        SetupGate.SHOWING -> when {
            inputs.skipped -> SetupGate.CLOSED

            // Not before the restore is done: the first distro to land is not the default yet.
            inputs.restoring -> SetupGate.SHOWING

            hasReady(inputs.distros) -> SetupGate.CLOSED

            else -> SetupGate.SHOWING
        }
    }

    /**
     * The distro to make the default when some are ready and none is: so the first tab opens it
     * (RF-13) even if an earlier leftover row held the default flag.
     */
    fun distroToMakeDefault(distros: List<Distro>): Distro? {
        val ready = distros.filter { it.state == DistroState.READY }
        if (ready.isEmpty() || ready.any { it.isDefault }) return null
        return ready.maxBy { it.id }
    }

    /** The form can be sent: a name, and a user `useradd` accepts. */
    fun canInstall(name: String, user: String): Boolean =
        name.isNotBlank() && GuestUser.isValid(user.trim())

    private fun hasReady(distros: List<Distro>) = distros.any { it.state == DistroState.READY }
}
