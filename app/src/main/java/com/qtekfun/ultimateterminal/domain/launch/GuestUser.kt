// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

/** User names inside a distro, and where their home is. */
object GuestUser {
    private val VALID = Regex("[a-z_][a-z0-9_-]{0,31}")
    const val ROOT = "root"

    /** The shape `useradd` accepts. A name starting with `-` could be read as an option by `su`. */
    fun isValid(name: String): Boolean = VALID.matches(name)

    fun isRoot(name: String?): Boolean = name == null || name == ROOT

    fun homeOf(name: String?): String = if (isRoot(name)) "/root" else "/home/$name"
}
