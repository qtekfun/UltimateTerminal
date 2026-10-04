// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.storage

import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.session.distro
import com.qtekfun.ultimateterminal.fakes.FailingFileSystem
import com.qtekfun.ultimateterminal.fakes.FakeSharedStorageAccess
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedStorageMountsTest {
    private val fileSystem = InMemoryFileSystemRepository()
    private val access = FakeSharedStorageAccess()
    private val distro = distro(7)

    private fun path(raw: String) = checkNotNull(FsPath.of(raw).getOrNull())

    @Test
    fun `the mount points are created inside the root filesystem of the distro`() = runTest {
        val mounts = SharedStorageMounts(fileSystem, access)

        val plan = mounts.prepare(distro, enabled = true, guestHome = "/root")

        assertTrue(plan is StorageMountPlan.Active)
        listOf("shared", "downloads", "dcim", "documents").forEach {
            assertTrue(fileSystem.exists(path("distros/7/rootfs/root/storage/$it")), it)
        }
        assertFalse(fileSystem.exists(path("distros/7/rootfs/root/storage/music")))
    }

    @Test
    fun `preparing twice is fine because creating a directory that exists is not an error`() =
        runTest {
            val mounts = SharedStorageMounts(fileSystem, access)

            val first = mounts.prepare(distro, true, "/root")
            val second = mounts.prepare(distro, true, "/root")

            assertEquals(first, second)
        }

    @Test
    fun `a disabled setting creates nothing`() = runTest {
        val plan = SharedStorageMounts(fileSystem, access).prepare(distro, false, "/root")

        assertEquals(StorageMountPlan.Disabled, plan)
        assertFalse(fileSystem.exists(path("distros/7/rootfs/root")))
    }

    @Test
    fun `a degraded plan creates nothing and the session still starts`() = runTest {
        val plan = SharedStorageMounts(fileSystem, FakeSharedStorageAccess(granted = false))
            .prepare(distro, true, "/root")

        assertEquals(StorageMountPlan.Degraded(StorageDegradation.PERMISSION_DENIED), plan)
        assertFalse(fileSystem.exists(path("distros/7/rootfs/root")))
    }

    @Test
    fun `a full disk degrades the plan instead of failing the session`() = runTest {
        val failing = FailingFileSystem(fileSystem, failCreate = true)

        val plan = SharedStorageMounts(failing, access).prepare(distro, true, "/root")

        assertEquals(StorageMountPlan.Degraded(StorageDegradation.MOUNT_POINT_FAILED), plan)
    }

    @Test
    fun `a home that is not a safe path degrades the plan`() = runTest {
        val plan = SharedStorageMounts(fileSystem, access).prepare(distro, true, "/root/../etc")

        assertEquals(StorageMountPlan.Degraded(StorageDegradation.MOUNT_POINT_FAILED), plan)
    }
}
