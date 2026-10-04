// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.distro.DistroInstaller
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.launch.LaunchNotice
import com.qtekfun.ultimateterminal.domain.launch.LaunchProblem
import com.qtekfun.ultimateterminal.domain.launch.ShellRequest
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageMounts
import com.qtekfun.ultimateterminal.domain.storage.StorageMountPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Decides what a new tab starts: the Android shell, or proot with a distro and everything it needs
 * (the root filesystem, the shared storage of T13, a resolv.conf, the user). It touches no process:
 * it returns a [LaunchPlan] that the session factory carries out, so every rule here is tested on
 * the host. It never throws for a problem a user can have; those come back as [LaunchPlan.Failed]
 * or as a notice.
 */
@Singleton
class ProotSessionPlanner @Inject constructor(
    private val distros: DistroRepository,
    private val fileSystem: FileSystemRepository,
    private val settings: SettingsRepository,
    private val mounts: SharedStorageMounts,
    private val runtime: ProotRuntime,
    private val dns: ResolvConfSource
) {
    suspend fun plan(request: ShellRequest): LaunchPlan {
        val command = request.initialCommand
        val id = request.distroId
        return when {
            command != null && !isRunnable(command) ->
                LaunchPlan.Failed(LaunchProblem.InvalidCommand)

            id == null && command != null ->
                LaunchPlan.Failed(LaunchProblem.CommandNeedsDistro)

            id == null -> LaunchPlan.AndroidShell

            else -> planDistro(id, command)
        }
    }

    private suspend fun planDistro(id: Long, command: List<String>?): LaunchPlan {
        val distro = distros.get(id)
        return if (distro == null || distro.state != DistroState.READY) {
            unusable(distro?.name, command)
        } else {
            launchIn(distro, command)
        }
    }

    /** A command must not silently turn into an Android shell, so it fails; a plain tab falls back. */
    private fun unusable(name: String?, command: List<String>?): LaunchPlan = if (command != null) {
        LaunchPlan.Failed(LaunchProblem.DistroUnavailable(name))
    } else {
        LaunchPlan.FallbackToAndroid(LaunchNotice.DistroUnavailable(name))
    }

    private suspend fun launchIn(distro: Distro, command: List<String>?): LaunchPlan {
        val user = distro.defaultUser
        val rootfs = distro.directory.child(DistroInstaller.UNPACKED_NAME).getOrNull()
        val problem = when {
            !GuestUser.isValid(user) -> LaunchProblem.InvalidUser(user)
            rootfs == null || !fileSystem.exists(rootfs) -> LaunchProblem.DistroCorrupt(distro.name)
            !runtime.hasBinaries() -> LaunchProblem.ProotMissing
            else -> null
        }
        val tmpDir = if (problem == null) runtime.prepareTmpDir() else null
        return when {
            problem != null -> LaunchPlan.Failed(problem)
            rootfs == null -> LaunchPlan.Failed(LaunchProblem.DistroCorrupt(distro.name))
            tmpDir == null -> LaunchPlan.Failed(LaunchProblem.TempDirUnavailable)
            else -> buildLaunch(distro, fileSystem.absolutePathOf(rootfs), tmpDir, command)
        }
    }

    private suspend fun buildLaunch(
        distro: Distro,
        rootfsPath: String,
        tmpDir: String,
        command: List<String>?
    ): LaunchPlan {
        val user = distro.defaultUser.takeUnless(GuestUser::isRoot)
        val home = GuestUser.homeOf(user)
        val app = settings.observe().first()
        val storage = mounts.prepare(distro, app.sharedStorage, home)
        val resolvConf = dns.hostFile()
        val binds = ProotSession.DEFAULT_BINDS +
            listOfNotNull(resolvConf?.let { ProotBind(it, RESOLV_CONF_GUEST_PATH) })
        val session = ProotSession(
            rootfs = rootfsPath,
            // `su -l` changes to the user's home itself; a path that may not exist cannot be `-w`.
            workingDirectory = if (user == null) home else "/",
            binds = binds,
            disableSeccomp = app.prootCompatibilityMode,
            user = user,
            command = command
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

    /** A program name and arguments that can be passed to exec: not empty, no NUL. */
    private fun isRunnable(command: List<String>): Boolean =
        command.isNotEmpty() && command.first().isNotEmpty() &&
            command.none { it.contains('\u0000') }

    private companion object {
        const val RESOLV_CONF_GUEST_PATH = "/etc/resolv.conf"
    }
}
