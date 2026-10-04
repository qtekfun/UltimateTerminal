// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.rootfs

/** CPU architectures the app ships proot for (SPEC §2), tied to the Android ABI names. */
enum class Architecture(val abi: String) {
    ARM64("arm64-v8a"),
    ARMV7("armeabi-v7a"),
    X86_64("x86_64");

    companion object {
        /** The first of [abis] (in the device's preference order) that the app supports. */
        fun fromAbis(abis: List<String>): Architecture? =
            abis.firstNotNullOfOrNull { abi -> entries.firstOrNull { it.abi == abi } }
    }
}

/** Distributions offered in the MVP (SPEC §2). */
enum class DistroFamily { DEBIAN, UBUNTU, ALPINE }

/**
 * An official root filesystem archive: where to download it and what it must hash to.
 *
 * [sha256] is lowercase hex. [sizeBytes] is null when the official index does not publish a size
 * (Ubuntu); the hash is what is authoritative either way.
 */
data class RootfsSource(
    val family: DistroFamily,
    val architecture: Architecture,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long?
)

data class DownloadProgress(val bytesDone: Long, val totalBytes: Long?) {
    /** 0.0 to 1.0, or null while the total is unknown. */
    val fraction: Float?
        get() = totalBytes?.takeIf {
            it > 0
        }?.let { (bytesDone.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}
