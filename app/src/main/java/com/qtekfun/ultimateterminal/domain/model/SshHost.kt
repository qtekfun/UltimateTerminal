// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

/** A saved server (SPEC RF-09). [id] is 0 for a host that has not been saved yet. */
data class SshHost(
    val id: Long = 0L,
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val user: String,
    /** Alias of the private key in the app's key store; never the key itself. */
    val keyAlias: String? = null,
    /** The distro whose `ssh` is used; null means the default distro. */
    val distroId: Long? = null
) {
    companion object {
        const val DEFAULT_PORT = 22
    }
}
