// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

/**
 * POSIX single-quote escaping. Used only where a command line has to go through one shell (`su -c`
 * for a distro user that is not root); everywhere else the arguments stay a list.
 */
object ShellQuote {
    /** `it's` becomes `'it'\''s'`: the only character a single-quoted string cannot hold is `'`. */
    fun quote(argument: String): String = "'" + argument.replace("'", "'\\''") + "'"

    fun join(arguments: List<String>): String = arguments.joinToString(" ", transform = ::quote)
}

/** User names inside a distro, and where their home is. */
object GuestUser {
    private val VALID = Regex("[a-z_][a-z0-9_-]{0,31}")
    const val ROOT = "root"

    /** The shape `useradd` accepts. A name starting with `-` could be read as an option by `su`. */
    fun isValid(name: String): Boolean = VALID.matches(name)

    fun isRoot(name: String?): Boolean = name == null || name == ROOT

    fun homeOf(name: String?): String = if (isRoot(name)) "/root" else "/home/$name"
}
