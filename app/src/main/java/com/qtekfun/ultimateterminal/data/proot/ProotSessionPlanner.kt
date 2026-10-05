// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.launch.GuestAccountResolver
import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.launch.LaunchNotice
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageMounts
import com.qtekfun.ultimateterminal.domain.storage.StorageMountPlan
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Decides what a new tab starts when the caller did not give a launch of its own: the Android shell,
 * or proot with a distro and everything it needs (the root filesystem, the shared storage of T13, a
 * resolv.conf, the user). A caller with its own command (the SSH hosts screen) builds its launch
 * itself and never reaches this class: there is one source of "what to run" per tab (D-T08b-9).
 *
 * It touches no process: it returns a [LaunchPlan] that the session factory carries out, so every
 * rule here is tested on the host. It never throws for a problem a user can have; those come back
 * as [LaunchPlan.Failed] or as a notice.
 */
class ProotSessionPlanner @Inject constructor(
    private val distros: DistroRepository,
    private val fileSystem: FileSystemRepository,
    private val settings: SettingsRepository,
    private val mounts: SharedStorageMounts,
    private val runtime: ProotRuntime,
    private val dns: ResolvConfSource,
    private val fakeProc: FakeProcSource = FakeProcSource.None
) {
    private val accounts = GuestAccountResolver(fileSystem)

    /**
     * [distroId] is the distro the tab was opened in, null for the Android shell. [user] is the
     * user a profile asks for (T12b); null means the distro's own default user.
     */
    suspend fun plan(distroId: Long?, user: String? = null): LaunchPlan {
        if (distroId == null) return LaunchPlan.AndroidShell
        val distro = distros.get(distroId)
        return if (distro == null || distro.state != DistroState.READY) {
            LaunchPlan.FallbackToAndroid(LaunchNotice.DistroUnavailable(distro?.name))
        } else {
            launchIn(distro, user ?: distro.defaultUser)
        }
    }

    private suspend fun launchIn(distro: Distro, user: String): LaunchPlan {
        // The distro's directory is the root filesystem itself: the installer moves what it unpacked
        // there (see DistroInstaller), so there is no `rootfs` folder inside it.
        val problem = when {
            !GuestUser.isValid(user) -> LaunchProblem.InvalidUser(user)
            !fileSystem.exists(distro.directory) -> LaunchProblem.DistroCorrupt(distro.name)
            !runtime.hasBinaries() -> LaunchProblem.ProotMissing
            else -> null
        }
        val tmpDir = if (problem == null) runtime.prepareTmpDir() else null
        return when {
            problem != null -> LaunchPlan.Failed(problem)
            tmpDir == null -> LaunchPlan.Failed(LaunchProblem.TempDirUnavailable)
            else -> buildLaunch(distro, user, tmpDir)
        }
    }

    private suspend fun buildLaunch(distro: Distro, userName: String, tmpDir: String): LaunchPlan {
        val user = userName.takeUnless(GuestUser::isRoot)
        // No `su` in the guest (Fedora ships none): the user's ids come from /etc/passwd (D-USER-1).
        val login = user?.let { accounts.resolve(distro.directory, it) }
        if (user != null &&
            login == null
        ) {
            return LaunchPlan.Failed(LaunchProblem.UserUnavailable(user))
        }
        val home = login?.account?.home ?: GuestUser.homeOf(user)
        val app = settings.observe().first()
        val storage = mounts.prepare(distro, app.sharedStorage, home)
        val resolvConf = dns.hostFile()
        val session = ProotSession(
            rootfs = fileSystem.absolutePathOf(distro.directory),
            workingDirectory = login?.workingDirectory ?: home,
            binds = ProotSession.DEFAULT_BINDS + resolvConfBinds(resolvConf) +
                fakeProcBinds(fakeProc.hostFiles()),
            disableSeccomp = app.prootCompatibilityMode,
            account = login?.account
        ).withSharedStorage(storage)
        val notice = when {
            storage is StorageMountPlan.Degraded -> LaunchNotice.StorageNotMounted(storage.reason)
            resolvConf == null -> LaunchNotice.DnsNotConfigured
            else -> null
        }
        return LaunchPlan.InDistro(
            launch = ProotCommandBuilder(runtime.nativeLibraryDir, tmpDir).build(session),
            distroName = distro.name,
            notice = notice
        )
    }
}
