// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError

/** What the user asks for when installing a distro. */
data class InstallRequest(
    val family: DistroFamily,
    val name: String,
    val user: String = NewDistro.DEFAULT_USER
)

/** The steps of an install, in order. The download step includes its SHA-256 check (T06). */
enum class InstallPhase { RESOLVING, DOWNLOADING, VERIFYING, EXTRACTING, FINALIZING }

/** [fraction] is 0.0 to 1.0, or null while the amount of work is not known. */
data class InstallProgress(val phase: InstallPhase, val fraction: Float? = null)

/** Why an install failed. It never carries secrets and is never thrown towards the UI. */
sealed interface InstallError {
    /** The device has no supported CPU architecture (SPEC §2 ships arm64, armv7 and x86_64). */
    data class UnsupportedArchitecture(val abis: List<String>) : InstallError

    /** The name or user is not valid, or the name is already used by another distro. */
    data class InvalidRequest(val error: DomainError) : InstallError

    data class Catalog(val error: RootfsError) : InstallError

    data class Download(val error: RootfsError) : InstallError

    data class Extraction(val error: ExtractionError) : InstallError

    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) : InstallError

    data class Storage(val message: String) : InstallError
}

sealed interface InstallResult {
    data class Success(val distro: Distro) : InstallResult

    data class Failure(val error: InstallError) : InstallResult
}
