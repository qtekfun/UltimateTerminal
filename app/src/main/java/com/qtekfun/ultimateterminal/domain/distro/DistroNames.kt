// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

/** Suggests names for new distros; the user can change them, uniqueness is checked on save. */
object DistroNames {
    /**
     * [base] when it is free, otherwise "[base] 2", "[base] 3"... Names are compared ignoring case,
     * the same way the repository does.
     */
    fun suggest(base: String, existing: Collection<String>): String {
        val taken = existing.mapTo(HashSet()) { it.lowercase() }
        if (base.lowercase() !in taken) return base
        var number = 2
        while ("$base $number".lowercase() in taken) number++
        return "$base $number"
    }

    /** The suggestion for a copy of [name]: "[name] copy", numbered when that is taken too. */
    fun suggestCopy(name: String, existing: Collection<String>): String =
        suggest("$name copy", existing)
}
