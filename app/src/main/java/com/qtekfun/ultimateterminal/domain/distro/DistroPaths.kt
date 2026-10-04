// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath

/**
 * Where distros live inside the storage root. Every install gets its own random token, so two
 * installs (or an install and a leftover of a crashed one) can never share a directory.
 */
object DistroPaths {
    private const val DISTROS = "distros"
    private const val STAGING = "distros-tmp"

    /** Everything an interrupted install may have left; safe to delete when no install runs. */
    fun stagingRoot(): FsPath = fixed(STAGING)

    fun distroDirectory(token: String): FsPath = fixed("$DISTROS/$token")

    fun stagingDirectory(token: String): FsPath = fixed("$STAGING/$token")

    /** The tokens come from a UUID, so the path is always valid; a bad token is a bug. */
    private fun fixed(raw: String): FsPath = requireNotNull(FsPath.of(raw).getOrNull()) {
        "not a safe path: $raw"
    }
}

/** How much free space an install needs; a refusal up front beats a half-written rootfs. */
object InstallSpace {
    /** A gzip root filesystem unpacks to a few times its size; the archive is kept while unpacking. */
    private const val EXPANSION = 4L
    private const val MINIMUM_BYTES = 64L * 1024 * 1024

    /** Used when the official index does not say how big the archive is (Ubuntu). */
    private const val UNKNOWN_ARCHIVE_BYTES = 512L * 1024 * 1024

    fun requiredBytes(archiveBytes: Long?): Long {
        val archive = archiveBytes?.takeIf { it > 0 } ?: return UNKNOWN_ARCHIVE_BYTES
        return maxOf(MINIMUM_BYTES, archive * (1 + EXPANSION))
    }
}
