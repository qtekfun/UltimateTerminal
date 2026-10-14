// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.session.SessionLaunch
import com.qtekfun.ultimateterminal.domain.ssh.SshKnownHosts
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
    private val dns: ResolvConfSource,
    private val cleanupScope: CoroutineScope,
    private val fakeProc: FakeProcSource = FakeProcSource.None
) {
    /**
     * The same resolver as every other tab gets (the device's DNS, see [ResolvConfSource]), bound over
     * the distro's own `/etc/resolv.conf`, so `ssh` can resolve a host name.
     */
    suspend fun create(plan: SshLaunchPlan): SessionLaunch {
        val session = ProotSession(
            rootfs = fileSystem.absolutePathOf(plan.distro.directory),
            binds = ProotSession.DEFAULT_BINDS + resolvConfBinds(dns.hostFile()) +
                fakeProcBinds(fakeProc.hostFiles()) + knownHostsBinds(),
            command = plan.guestCommand
        )
        val launch = proot.build(session)
        return SessionLaunch(launch.command, launch.environment) {
            cleanupScope.launch { plan.cleanup() }
        }
    }

    /**
     * The folder of trusted host keys, bound into the guest (D-SSH-2). The app creates it, so it is
     * writable whatever the state of the distro's own `/root`. Without it ssh still connects: it
     * only cannot remember the host.
     */
    private suspend fun knownHostsBinds(): List<ProotBind> {
        val directory = FsPath.of(SshKnownHosts.STORAGE_DIRECTORY).getOrNull()
        val ready = directory != null && fileSystem.createDirectories(directory) is Outcome.Success
        return if (ready) {
            listOf(ProotBind(fileSystem.absolutePathOf(directory), SshKnownHosts.GUEST_DIRECTORY))
        } else {
            emptyList()
        }
    }
}
