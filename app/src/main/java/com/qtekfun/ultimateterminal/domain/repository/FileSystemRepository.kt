// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.repository

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.FsPath

/**
 * Everything the app does to files in its private storage (root filesystems, backups). It is the
 * only way `ui` and `domain` reach the disk, so they never use `java.io.File` (CLAUDE.md).
 *
 * Every path is relative to the storage root. An operation never follows a symbolic link that
 * lies on the way to its target, because a root filesystem is full of links that point outside it.
 */
interface FileSystemRepository {
    suspend fun exists(path: FsPath): Boolean

    suspend fun createDirectories(path: FsPath): Outcome<Unit>

    /** Deletes a file or a whole tree, without following links, even when directories are read-only. */
    suspend fun deleteRecursively(path: FsPath): Outcome<Unit>

    /**
     * Copies a tree keeping symbolic links, permissions and times. It is all or nothing: the
     * copy is built next to the destination and renamed into place, so a failure leaves nothing.
     * Special files (sockets, pipes, devices) are skipped.
     */
    suspend fun copyRecursively(from: FsPath, to: FsPath): Outcome<Unit>

    /** Renames [from] to [to] inside the storage; fails if [to] exists. */
    suspend fun move(from: FsPath, to: FsPath): Outcome<Unit>

    /** Total size in bytes of the regular files under [path]. */
    suspend fun sizeOf(path: FsPath): Outcome<Long>

    suspend fun freeSpaceBytes(): Long

    /**
     * The absolute location of [path] on the device, to build command lines (proot's `-r`). It is a
     * string on purpose; do not turn it back into a file outside the data layer.
     */
    fun absolutePathOf(path: FsPath): String
}
