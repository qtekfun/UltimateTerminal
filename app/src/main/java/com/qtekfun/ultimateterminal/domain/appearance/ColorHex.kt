// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import java.util.Locale

/** Colors as the user types and reads them: "#rrggbb" (also "rrggbb" and "#rgb"). */
object ColorHex {
    private const val HEX_RADIX = 16
    private const val RGB_MASK = 0xFFFFFF
    private const val SHORT_LENGTH = 3
    private const val LONG_LENGTH = 6
    private val digits = Regex("^[0-9a-fA-F]+$")

    /** An opaque ARGB int, or null when [text] is not a color. */
    fun parse(text: String): Int? {
        val body = text.trim().removePrefix("#")
        val full = when {
            !digits.matches(body) -> null
            body.length == SHORT_LENGTH -> body.map { "$it$it" }.joinToString("")
            body.length == LONG_LENGTH -> body
            else -> null
        }
        return full?.let { it.toInt(HEX_RADIX) or TerminalColorScheme.BLACK }
    }

    /** "#rrggbb", lower case. */
    fun format(argb: Int): String = "#%06x".format(Locale.ROOT, argb and RGB_MASK)
}
