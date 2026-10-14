// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/**
 * The "word" under a cell, as a terminal user means it: a run of non-blank characters, so a path, a
 * URL or an option is picked whole. Columns are taken as character indexes, which is exact for
 * narrow characters (the common case in a shell) and approximate next to wide ones.
 */
object WordBounds {
    /** The columns of the word that contains [column] in [line], or null on a blank or past the end. */
    fun at(line: String, column: Int): IntRange? {
        if (column !in line.indices || line[column].isWhitespace()) return null
        var first = column
        while (first > 0 && !line[first - 1].isWhitespace()) first--
        var last = column
        while (last < line.lastIndex && !line[last + 1].isWhitespace()) last++
        return first..last
    }
}
