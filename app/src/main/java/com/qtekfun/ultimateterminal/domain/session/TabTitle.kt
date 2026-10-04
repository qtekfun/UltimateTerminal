// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** The rules for a tab name the user types. */
object TabTitle {
    const val MAX_LENGTH = 32

    /**
     * The name to store: control characters (a pasted line break) are dropped, the ends are
     * trimmed and the length is limited without cutting a character in two. Null for a blank name,
     * which means "use the default name".
     */
    fun normalize(raw: String?): String? {
        val cleaned = raw.orEmpty().filterNot { it.isISOControl() }.trim()
        var cut = cleaned.take(MAX_LENGTH)
        if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        return cut.trim().ifEmpty { null }
    }
}
