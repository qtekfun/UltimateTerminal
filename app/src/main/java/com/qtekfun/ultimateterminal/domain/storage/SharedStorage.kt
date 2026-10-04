// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.storage

/** A host directory and where a distro sees it. */
data class StorageBind(val hostPath: String, val guestPath: String)

/** Why the shared storage could not be mounted although the user asked for it. */
enum class StorageDegradation {
    /** The storage permission is not granted (it can be revoked in the system settings). */
    PERMISSION_DENIED,

    /** The shared storage is missing or not mounted (an unmounted card, a locked profile). */
    STORAGE_UNAVAILABLE,

    /** The mount points could not be created inside the distro (full disk, a file in the way). */
    MOUNT_POINT_FAILED
}

/** What a session should do about the shared storage. A session always starts, whatever the plan. */
sealed interface StorageMountPlan {
    /** The user did not ask for it. */
    data object Disabled : StorageMountPlan

    /** The user asked for it but it cannot be offered now; the session starts without it. */
    data class Degraded(val reason: StorageDegradation) : StorageMountPlan

    /** [binds] are added to proot; their guest paths are directories that must exist in the distro. */
    data class Active(val binds: List<StorageBind>) : StorageMountPlan
}

/** What the device says about the shared storage. The Android implementation lives in `platform`. */
interface SharedStorageAccess {
    /** Whether the app may read the shared storage right now. */
    fun isPermissionGranted(): Boolean

    /** The absolute path of the primary shared storage, or null if it is not available. */
    fun sharedRoot(): String?

    fun directoryExists(absolutePath: String): Boolean
}

/** The folders of the shared storage the distro gets, and their names inside `~/storage`. */
object SharedStorageLayout {
    /** `~/storage/shared` is the whole shared storage; the others are shortcuts to common folders. */
    const val SHARED_NAME = "shared"

    private const val STORAGE_DIR = "storage"

    /** A folder of the shared storage ([host], relative to its root) and its name in `~/storage`. */
    data class Folder(val host: String, val guest: String)

    val FOLDERS = listOf(
        Folder("Download", "downloads"),
        Folder("DCIM", "dcim"),
        Folder("Documents", "documents"),
        Folder("Pictures", "pictures"),
        Folder("Music", "music"),
        Folder("Movies", "movies")
    )

    fun storageDirOf(guestHome: String): String = "${guestHome.trimEnd('/')}/$STORAGE_DIR"
}

object SharedStoragePlanner {
    /**
     * Decides what to mount. The order matters: a disabled setting never asks about the permission
     * (so nothing is requested or checked unless the user turned the feature on), and a missing
     * permission is reported before a missing storage because the user can fix it.
     */
    fun plan(enabled: Boolean, access: SharedStorageAccess, guestHome: String): StorageMountPlan =
        when {
            !enabled -> StorageMountPlan.Disabled

            !access.isPermissionGranted() -> StorageMountPlan.Degraded(
                StorageDegradation.PERMISSION_DENIED
            )

            else -> mounts(access, guestHome)
        }

    private fun mounts(access: SharedStorageAccess, guestHome: String): StorageMountPlan {
        val root =
            access.sharedRoot()?.trimEnd('/')?.takeIf {
                it.isNotEmpty() &&
                    access.directoryExists(it)
            }
                ?: return StorageMountPlan.Degraded(StorageDegradation.STORAGE_UNAVAILABLE)
        val storage = SharedStorageLayout.storageDirOf(guestHome)
        val folders = SharedStorageLayout.FOLDERS
            .filter { access.directoryExists("$root/${it.host}") }
            .map { StorageBind("$root/${it.host}", "$storage/${it.guest}") }
        val whole = StorageBind(root, "$storage/${SharedStorageLayout.SHARED_NAME}")
        return StorageMountPlan.Active(listOf(whole) + folders)
    }
}
