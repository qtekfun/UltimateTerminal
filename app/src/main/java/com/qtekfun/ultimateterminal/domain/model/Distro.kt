// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

import java.time.Instant

enum class DistroType { DEBIAN, UBUNTU, ALPINE }

/** Installing and failed distros are kept as rows so an interrupted install can be cleaned up. */
enum class DistroState { INSTALLING, READY, FAILED }

/** An installed Linux distribution (SPEC §5). Its root filesystem lives at [directory]. */
data class Distro(
    val id: Long,
    val name: String,
    val type: DistroType,
    /** Distribution release, as the user sees it (e.g. "12", "24.04", "3.20"). */
    val release: String,
    /** Location of the root filesystem, relative to the storage root, so backups stay portable. */
    val directory: FsPath,
    val defaultUser: String,
    val state: DistroState,
    val sizeBytes: Long,
    val installedAt: Instant,
    val isDefault: Boolean
)

/** What is needed to register a distro; the repository fills in the rest. */
data class NewDistro(
    val name: String,
    val type: DistroType,
    val release: String,
    val directory: FsPath,
    val defaultUser: String = DEFAULT_USER
) {
    companion object {
        const val DEFAULT_USER = "root"
    }
}
