// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** What a tab is called, before it is turned into text in the user's language. */
sealed interface TabName {
    /** The name the user gave it. */
    data class Custom(val text: String) : TabName

    /** The profile it was opened with; [ordinal] says which tab of that profile it is, from 1. */
    data class InProfile(val profile: String, val ordinal: Int) : TabName

    /** The distro it runs in; [ordinal] says which of the tabs of that distro it is, from 1. */
    data class InDistro(val distro: String, val ordinal: Int) : TabName

    /** The Android shell, or a tab whose distro is not known: "Shell 3". */
    data class Plain(val number: Int) : TabName
}

/**
 * The name of each tab, in bar order. A name the user typed always wins. Otherwise a tab takes the
 * name of its profile ("dev"), else of its distro ("Alpine"), numbered when several tabs share
 * one and have no name of their own ("Alpine", "Alpine 2"), and a tab in no distro keeps the plain "Shell N".
 */
fun tabNames(items: List<TabItem>): List<TabName> {
    val seen = mutableMapOf<String, Int>()
    val seenProfiles = mutableMapOf<String, Int>()
    return items.map { item ->
        val title = item.title
        val distro = item.distroName
        val profile = item.profileName
        when {
            title != null -> TabName.Custom(title)

            profile != null -> {
                val ordinal = (seenProfiles[profile] ?: 0) + 1
                seenProfiles[profile] = ordinal
                TabName.InProfile(profile, ordinal)
            }

            distro != null -> {
                val ordinal = (seen[distro] ?: 0) + 1
                seen[distro] = ordinal
                TabName.InDistro(distro, ordinal)
            }

            else -> TabName.Plain(item.id.value)
        }
    }
}
