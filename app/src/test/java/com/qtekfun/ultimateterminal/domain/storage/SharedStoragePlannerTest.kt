// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.storage

import com.qtekfun.ultimateterminal.fakes.FakeSharedStorageAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedStoragePlannerTest {
    private val root = "/storage/emulated/0"

    @Test
    fun `a disabled setting mounts nothing and does not even ask for the permission`() {
        val access = FakeSharedStorageAccess(granted = false)

        val plan = SharedStoragePlanner.plan(enabled = false, access = access, guestHome = "/root")

        assertEquals(StorageMountPlan.Disabled, plan)
        assertEquals(0, access.permissionChecks)
    }

    @Test
    fun `without the permission the plan is degraded and says why`() {
        val plan = SharedStoragePlanner.plan(
            true,
            FakeSharedStorageAccess(granted = false),
            "/root"
        )

        assertEquals(StorageMountPlan.Degraded(StorageDegradation.PERMISSION_DENIED), plan)
    }

    @Test
    fun `a missing permission is reported before a missing storage`() {
        val access = FakeSharedStorageAccess(granted = false, root = null)

        assertEquals(
            StorageMountPlan.Degraded(StorageDegradation.PERMISSION_DENIED),
            SharedStoragePlanner.plan(true, access, "/root")
        )
    }

    @Test
    fun `no shared storage root degrades the plan`() {
        val plan = SharedStoragePlanner.plan(true, FakeSharedStorageAccess(root = null), "/root")

        assertEquals(StorageMountPlan.Degraded(StorageDegradation.STORAGE_UNAVAILABLE), plan)
    }

    @Test
    fun `a root that is not a directory or is blank degrades the plan`() {
        val gone = FakeSharedStorageAccess(directories = emptySet())
        val blank = FakeSharedStorageAccess(root = "")
        val slash = FakeSharedStorageAccess(root = "/")

        listOf(gone, blank, slash).forEach {
            assertEquals(
                StorageMountPlan.Degraded(StorageDegradation.STORAGE_UNAVAILABLE),
                SharedStoragePlanner.plan(true, it, "/root")
            )
        }
    }

    @Test
    fun `the whole storage and the folders that exist are mounted in the home`() {
        val plan = SharedStoragePlanner.plan(true, FakeSharedStorageAccess(), "/root")

        assertEquals(
            StorageMountPlan.Active(
                listOf(
                    StorageBind(root, "/root/storage/shared"),
                    StorageBind("$root/Download", "/root/storage/downloads"),
                    StorageBind("$root/DCIM", "/root/storage/dcim"),
                    StorageBind("$root/Documents", "/root/storage/documents")
                )
            ),
            plan
        )
    }

    @Test
    fun `a folder that does not exist on the device is left out`() {
        val access = FakeSharedStorageAccess(directories = setOf(root, "$root/Download"))

        val plan = SharedStoragePlanner.plan(true, access, "/root") as StorageMountPlan.Active

        assertEquals(
            listOf("/root/storage/shared", "/root/storage/downloads"),
            plan.binds.map { it.guestPath }
        )
    }

    @Test
    fun `a trailing slash in the root or in the home does not double the separators`() {
        val access = FakeSharedStorageAccess(
            root = "$root/",
            directories = setOf(root, "$root/Download")
        )

        val plan = SharedStoragePlanner.plan(true, access, "/home/user/") as StorageMountPlan.Active

        assertEquals(
            listOf(
                StorageBind(root, "/home/user/storage/shared"),
                StorageBind("$root/Download", "/home/user/storage/downloads")
            ),
            plan.binds
        )
    }
}
