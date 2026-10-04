// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome

/** The built-in schemes plus the ones the user imported. */
object SchemeCatalog {
    const val MAX_CUSTOM_SCHEMES = 50

    /** The scheme with [id], or the default one when it is gone (e.g. an imported one was removed). */
    fun resolve(id: String, custom: List<TerminalColorScheme>): TerminalColorScheme =
        (BuiltInSchemes.all + custom).firstOrNull { it.id == id } ?: BuiltInSchemes.dracula

    /** [custom] with [imported] added; names are unique among all schemes, ignoring case. */
    fun add(
        custom: List<TerminalColorScheme>,
        imported: TerminalColorScheme
    ): Outcome<List<TerminalColorScheme>> {
        val taken = (BuiltInSchemes.all + custom).any {
            it.name.equals(imported.name, ignoreCase = true)
        }
        return when {
            taken -> Outcome.Failure(DomainError.NameTaken(imported.name))

            custom.size >= MAX_CUSTOM_SCHEMES ->
                Outcome.Failure(DomainError.InvalidValue("customSchemes"))

            else -> Outcome.Success(custom + imported)
        }
    }

    fun remove(custom: List<TerminalColorScheme>, id: String): List<TerminalColorScheme> =
        custom.filterNot { it.id == id }
}
