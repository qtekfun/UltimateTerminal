// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.session.SessionLaunch
import com.qtekfun.ultimateterminal.domain.ssh.SshLaunchPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Turns a plan to run a command in a distro into the process to start: proot with the distro's
 * root filesystem, running that command. [cleanupScope] outlives any screen, so the key file of a
 * connection is removed even when the tab is closed after the activity is gone.
 */
class DistroLaunchFactory(
    private val proot: ProotCommandBuilder,
    private val fileSystem: FileSystemRepository,
    private val cleanupScope: CoroutineScope
) {
    fun create(plan: SshLaunchPlan): SessionLaunch {
        val session = ProotSession(
            rootfs = fileSystem.absolutePathOf(plan.distro.directory),
            shell = plan.guestCommand
        )
        val launch = proot.build(session)
        return SessionLaunch(launch.command, launch.environment) {
            cleanupScope.launch { plan.cleanup() }
        }
    }
}
