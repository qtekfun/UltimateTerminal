// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.rootfs

/** Finds the current official archive of a distribution for an architecture. */
interface RootfsCatalog {
    suspend fun resolve(
        family: DistroFamily,
        architecture: Architecture
    ): RootfsResult<RootfsSource>
}

/**
 * Downloads a [RootfsSource] to [destinationPath]. The file only appears at that path once its size
 * and SHA-256 have been verified: nothing unverified is ever left there.
 */
interface RootfsDownloader {
    suspend fun download(
        source: RootfsSource,
        destinationPath: String,
        onProgress: (DownloadProgress) -> Unit = {}
    ): RootfsResult<Unit>
}
