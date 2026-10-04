// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/** What to send to the terminal so it shows the new text: [deleteCount] backspaces, then [insert]. */
data class CompositionEdit(val deleteCount: Int, val insert: String) {
    val isEmpty: Boolean get() = deleteCount == 0 && insert.isEmpty()
}

/**
 * Soft keyboards compose a word before committing it, and may not commit until the keyboard is
 * hidden. A terminal cannot wait: what is typed has to reach the shell as it is typed. This keeps
 * what has already been sent for the word being composed and, each time the keyboard changes it,
 * returns the smallest edit that makes the shell's line match: the characters that changed are
 * erased and the new ones typed. Finishing the composition keeps what was sent.
 */
class ComposingText {
    private var sent = ""

    /** The edit that turns what was sent into [text]; the composition is now [text]. */
    fun update(text: String): CompositionEdit {
        val common = sent.commonPrefixWith(text).length
        val edit =
            CompositionEdit(deleteCount = sent.length - common, insert = text.substring(common))
        sent = text
        return edit
    }

    /** The composition is over: what was sent stays, and the next word starts empty. */
    fun finish() {
        sent = ""
    }

    /** A delete that removes [count] characters before the cursor; returns how many reach the shell. */
    fun deleteBefore(count: Int): Int {
        val inComposition = minOf(count, sent.length)
        sent = sent.dropLast(inComposition)
        return count
    }

    val isComposing: Boolean get() = sent.isNotEmpty()
}
