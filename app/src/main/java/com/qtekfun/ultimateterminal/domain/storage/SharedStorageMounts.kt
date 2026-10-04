// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.storage

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.distro.DistroInstaller
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository

/**
 * Prepares the shared storage for a session of [distro]: plans the mounts and creates, inside the
 * root filesystem, the empty directories proot mounts onto, so `ls ~/storage` lists them.
 *
 * It never fails a session: whatever goes wrong comes back as [StorageMountPlan.Degraded] and the
 * session starts without the shared storage.
 */
class SharedStorageMounts(
    private val fileSystem: FileSystemRepository,
    private val access: SharedStorageAccess
) {
    /** Creating a directory that exists is not an error, so calling this on every start is fine. */
    suspend fun prepare(distro: Distro, enabled: Boolean, guestHome: String): StorageMountPlan {
        val plan = SharedStoragePlanner.plan(enabled, access, guestHome)
        val failed = plan is StorageMountPlan.Active && !createMountPoints(distro, plan)
        return if (failed) {
            StorageMountPlan.Degraded(
                StorageDegradation.MOUNT_POINT_FAILED
            )
        } else {
            plan
        }
    }

    private suspend fun createMountPoints(distro: Distro, plan: StorageMountPlan.Active): Boolean {
        val rootfs =
            distro.directory.child(DistroInstaller.UNPACKED_NAME).getOrNull() ?: return false
        return plan.binds.all { bind ->
            val mountPoint = rootfs.child(bind.guestPath.trimStart('/')).getOrNull()
            mountPoint != null && fileSystem.createDirectories(mountPoint) is Outcome.Success
        }
    }
}
