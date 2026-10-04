// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.fakes

import com.qtekfun.ultimateterminal.domain.storage.SharedStorageAccess

/** A device whose shared storage and permission the test chooses. */
class FakeSharedStorageAccess(
    var granted: Boolean = true,
    var root: String? = "/storage/emulated/0",
    directories: Set<String> = setOf(
        "/storage/emulated/0",
        "/storage/emulated/0/Download",
        "/storage/emulated/0/DCIM",
        "/storage/emulated/0/Documents"
    )
) : SharedStorageAccess {
    val directories = directories.toMutableSet()
    var permissionChecks = 0
        private set

    override fun isPermissionGranted(): Boolean {
        permissionChecks++
        return granted
    }

    override fun sharedRoot(): String? = root

    override fun directoryExists(absolutePath: String) = absolutePath in directories
}
