// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WordBoundsTest {
    private val line = "ls -la /usr/local/bin  done"

    @Test
    fun picksTheWholePathUnderTheCell() {
        assertEquals(7..20, WordBounds.at(line, 12))
        assertEquals(7..20, WordBounds.at(line, 7))
        assertEquals(7..20, WordBounds.at(line, 20))
    }

    @Test
    fun picksAWordAtTheStartAndAtTheEndOfTheLine() {
        assertEquals(0..1, WordBounds.at(line, 0))
        assertEquals(23..26, WordBounds.at(line, 26))
    }

    @Test
    fun aBlankCellHasNoWord() {
        assertNull(WordBounds.at(line, 2))
        assertNull(WordBounds.at(line, 21))
    }

    @Test
    fun outsideTheLineHasNoWord() {
        assertNull(WordBounds.at(line, -1))
        assertNull(WordBounds.at(line, line.length))
        assertNull(WordBounds.at("", 0))
    }
}
