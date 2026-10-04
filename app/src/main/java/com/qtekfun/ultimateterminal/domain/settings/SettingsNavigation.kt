// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

/** The pages of the settings screen. The root lists the sections; the others are inside one. */
enum class SettingsPage {
    ROOT,
    TERMINAL,
    KEYBOARD,
    KEYBOARD_KEYS,
    SHORTCUTS,
    SESSIONS,
    DISTROS,
    STORAGE,
    NETWORK,
    BACKUP,
    ABOUT,
    NOTICES
}

/** The page a back button goes to from this one: the section for its sub-pages, otherwise the root. */
val SettingsPage.parent: SettingsPage
    get() = when (this) {
        SettingsPage.KEYBOARD_KEYS, SettingsPage.SHORTCUTS -> SettingsPage.KEYBOARD
        SettingsPage.NOTICES -> SettingsPage.ABOUT
        else -> SettingsPage.ROOT
    }

/**
 * The pages the user has opened, as a stack with the root at the bottom: going back pops one and
 * never leaves the root. Kept as plain data so it can be saved across a rotation.
 */
data class SettingsNavigation(val stack: List<SettingsPage> = listOf(SettingsPage.ROOT)) {
    val current: SettingsPage get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** Opens [page] on top. Opening the root goes back to it; opening the page already shown does nothing. */
    fun open(page: SettingsPage): SettingsNavigation = when {
        page == SettingsPage.ROOT -> SettingsNavigation()
        page == current -> this
        else -> SettingsNavigation(stack + page)
    }

    fun back(): SettingsNavigation = if (canGoBack) SettingsNavigation(stack.dropLast(1)) else this

    /** The stack as names, to save it; [fromNames] is its inverse and ignores names it does not know. */
    fun toNames(): List<String> = stack.map { it.name }

    companion object {
        fun fromNames(names: List<String>): SettingsNavigation {
            val pages = names.mapNotNull { name ->
                SettingsPage.entries.firstOrNull {
                    it.name ==
                        name
                }
            }
            return if (pages.firstOrNull() ==
                SettingsPage.ROOT
            ) {
                SettingsNavigation(pages)
            } else {
                SettingsNavigation()
            }
        }
    }
}
