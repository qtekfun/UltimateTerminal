// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComposingTextTest {
    private val composing = ComposingText()

    /** Replays the edits on a line, the way the shell does, to see what is left on screen. */
    private fun apply(line: String, edit: CompositionEdit) =
        line.dropLast(edit.deleteCount) + edit.insert

    @Test
    fun aWordTypedLetterByLetterSendsOnlyTheNewLetters() {
        assertEquals(CompositionEdit(0, "e"), composing.update("e"))
        assertEquals(CompositionEdit(0, "c"), composing.update("ec"))
        assertEquals(CompositionEdit(0, "ho"), composing.update("echo"))
    }

    @Test
    fun aCorrectionErasesWhatChangedAndTypesTheRest() {
        composing.update("hex")

        assertEquals(CompositionEdit(1, "y"), composing.update("hey"))
    }

    @Test
    fun replacingTheWholeWordErasesItAll() {
        composing.update("hola")

        assertEquals(CompositionEdit(4, "chau"), composing.update("chau"))
    }

    @Test
    fun clearingTheCompositionErasesIt() {
        composing.update("abc")

        assertEquals(CompositionEdit(3, ""), composing.update(""))
        assertFalse(composing.isComposing)
    }

    @Test
    fun committingTheSameTextSendsNothingMore() {
        composing.update("ls")

        val commit = composing.update("ls")
        composing.finish()

        assertTrue(commit.isEmpty)
        assertFalse(composing.isComposing)
    }

    @Test
    fun aWordThatIsCommittedWithoutComposingIsTypedWhole() {
        assertEquals(CompositionEdit(0, "pwd"), composing.update("pwd"))
    }

    @Test
    fun theNextWordStartsEmptyAfterFinishing() {
        composing.update("one")
        composing.finish()

        assertEquals(CompositionEdit(0, "two"), composing.update("two"))
    }

    @Test
    fun deletingInsideTheCompositionShortensIt() {
        composing.update("abc")

        assertEquals(2, composing.deleteBefore(2))
        assertEquals(CompositionEdit(0, "bd"), composing.update("abd"))
    }

    @Test
    fun deletingMoreThanWasComposedStillReachesTheShell() {
        composing.update("ab")

        assertEquals(5, composing.deleteBefore(5))
        assertFalse(composing.isComposing)
    }

    @Test
    fun theShellEndsUpWithWhatTheKeyboardShows() {
        var line = ""
        for (word in listOf("e", "ec", "ech", "echo", "ehco", "echo")) {
            line = apply(line, composing.update(word))
        }

        assertEquals("echo", line)
    }
}
