// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

/** What the planner needs from the device to run proot. The Android implementation is in `platform`. */
interface ProotRuntime {
    /** `ApplicationInfo.nativeLibraryDir`, where `libproot.so` and its loader are extracted. */
    val nativeLibraryDir: String

    /** Whether both binaries are there and executable. */
    fun hasBinaries(): Boolean

    /** A private writable directory for `PROOT_TMP_DIR`, created if needed; null if it cannot be. */
    fun prepareTmpDir(): String?
}

/** Provides the host file that is bound over a distro's `/etc/resolv.conf`. */
fun interface ResolvConfSource {
    /** The absolute path of a fresh resolv.conf, or null if it could not be written. */
    suspend fun hostFile(): String?
}

/** Where the guest reads its resolver configuration. */
const val RESOLV_CONF_GUEST_PATH = "/etc/resolv.conf"

/** The bind that puts [hostFile] over the guest's resolv.conf; none if the file could not be written. */
fun resolvConfBinds(hostFile: String?): List<ProotBind> =
    listOfNotNull(hostFile?.let { ProotBind(it, RESOLV_CONF_GUEST_PATH) })

/**
 * Provides the host files that are bound over the `/proc` files Android denies to apps (T08c).
 * The map goes from the name under `/proc` (`uptime`) to the absolute path of the file; empty if
 * they could not be written, in which case the session starts without them.
 */
fun interface FakeProcSource {
    suspend fun hostFiles(): Map<String, String>

    companion object {
        /** No fake files: the real `/proc` only. */
        val None = FakeProcSource { emptyMap() }
    }
}

/** The binds of the fake files, over the real `/proc`: they must come after `-b /proc`. */
fun fakeProcBinds(files: Map<String, String>): List<ProotBind> =
    files.toSortedMap().map { (name, hostFile) -> ProotBind(hostFile, "/proc/$name") }
