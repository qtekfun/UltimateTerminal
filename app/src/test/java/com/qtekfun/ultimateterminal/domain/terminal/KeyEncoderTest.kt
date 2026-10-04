// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyEncoderTest {
    private fun encode(input: KeyInput, cursorApp: Boolean = false, keypadApp: Boolean = false) =
        KeyEncoder.encode(input, cursorApp, keypadApp)

    @Test
    fun enterSendsCarriageReturn() {
        assertEquals(KeyOutput.Sequence("\r"), encode(KeyInput(KeyEvent.KEYCODE_ENTER, '\n'.code)))
    }

    @Test
    fun arrowKeysFollowTheCursorKeysMode() {
        val up = KeyInput(KeyEvent.KEYCODE_DPAD_UP, 0)
        assertEquals(KeyOutput.Sequence("\u001b[A"), encode(up))
        assertEquals(KeyOutput.Sequence("\u001bOA"), encode(up, cursorApp = true))
    }

    @Test
    fun modifiedArrowsCarryTheModifier() {
        val ctrlRight = KeyInput(KeyEvent.KEYCODE_DPAD_RIGHT, 0, ctrl = true)
        assertEquals(KeyOutput.Sequence("\u001b[1;5C"), encode(ctrlRight))
    }

    @Test
    fun functionKeysUseXtermSequences() {
        assertEquals(KeyOutput.Sequence("\u001bOP"), encode(KeyInput(KeyEvent.KEYCODE_F1, 0)))
        assertEquals(KeyOutput.Sequence("\u001b[15~"), encode(KeyInput(KeyEvent.KEYCODE_F5, 0)))
    }

    @Test
    fun backspaceSendsDelete() {
        assertEquals(KeyOutput.Sequence("\u007f"), encode(KeyInput(KeyEvent.KEYCODE_DEL, 0)))
    }

    @Test
    fun tabAndShiftTab() {
        assertEquals(KeyOutput.Sequence("\t"), encode(KeyInput(KeyEvent.KEYCODE_TAB, '\t'.code)))
        assertEquals(
            KeyOutput.Sequence("\u001b[Z"),
            encode(KeyInput(KeyEvent.KEYCODE_TAB, 0, shift = true))
        )
    }

    @Test
    fun plainCharactersAreSentAsTyped() {
        assertEquals(
            KeyOutput.CodePoint('a'.code, false),
            encode(KeyInput(KeyEvent.KEYCODE_A, 'a'.code))
        )
        assertEquals(
            KeyOutput.CodePoint('ñ'.code, false),
            encode(KeyInput(KeyEvent.KEYCODE_N, 'ñ'.code))
        )
    }

    @Test
    fun altPrefixesTheCharacterWithEscape() {
        assertEquals(
            KeyOutput.CodePoint('x'.code, true),
            encode(KeyInput(KeyEvent.KEYCODE_X, 'x'.code, alt = true))
        )
    }

    @Test
    fun ctrlLettersBecomeControlCharacters() {
        assertEquals(
            KeyOutput.CodePoint(3, false),
            encode(KeyInput(KeyEvent.KEYCODE_C, 'c'.code, ctrl = true))
        )
        assertEquals(
            KeyOutput.CodePoint(4, false),
            encode(KeyInput(KeyEvent.KEYCODE_D, 'D'.code, ctrl = true))
        )
        assertEquals(
            KeyOutput.CodePoint(26, false),
            encode(KeyInput(KeyEvent.KEYCODE_Z, 'z'.code, ctrl = true))
        )
    }

    @Test
    fun ctrlAltCombinesBoth() {
        assertEquals(
            KeyOutput.CodePoint(1, true),
            encode(KeyInput(KeyEvent.KEYCODE_A, 'a'.code, ctrl = true, alt = true))
        )
    }

    @Test
    fun ctrlPunctuationFollowsTheClassicTable() {
        fun ctrl(char: Char) = encode(KeyInput(0, char.code, ctrl = true))
        assertEquals(KeyOutput.CodePoint(0, false), ctrl('@'))
        assertEquals(KeyOutput.CodePoint(27, false), ctrl('['))
        assertEquals(KeyOutput.CodePoint(28, false), ctrl('\\'))
        assertEquals(KeyOutput.CodePoint(29, false), ctrl(']'))
        assertEquals(KeyOutput.CodePoint(30, false), ctrl('^'))
        assertEquals(KeyOutput.CodePoint(31, false), ctrl('_'))
        assertEquals(KeyOutput.CodePoint(127, false), ctrl('?'))
        assertEquals(KeyOutput.CodePoint('é'.code, false), ctrl('é'))
    }

    @Test
    fun ctrlDigitsAlias() {
        fun ctrl(char: Char) = encode(KeyInput(0, char.code, ctrl = true))
        assertEquals(KeyOutput.CodePoint(0, false), ctrl('2'))
        assertEquals(KeyOutput.CodePoint(27, false), ctrl('3'))
        assertEquals(KeyOutput.CodePoint(28, false), ctrl('4'))
        assertEquals(KeyOutput.CodePoint(29, false), ctrl('5'))
        assertEquals(KeyOutput.CodePoint(30, false), ctrl('6'))
        assertEquals(KeyOutput.CodePoint(31, false), ctrl('7'))
        assertEquals(KeyOutput.CodePoint(127, false), ctrl('8'))
    }

    @Test
    fun ctrlSpaceSendsNul() {
        assertEquals(
            KeyOutput.Sequence("\u0000"),
            encode(KeyInput(KeyEvent.KEYCODE_SPACE, ' '.code, ctrl = true))
        )
    }

    @Test
    fun aKeyThatTypesNothingSendsNothing() {
        assertEquals(KeyOutput.None, encode(KeyInput(KeyEvent.KEYCODE_SHIFT_LEFT, 0, shift = true)))
    }

    @Test
    fun numLockAndNumpadFollowTheKeypadMode() {
        val enter = KeyInput(KeyEvent.KEYCODE_NUMPAD_ENTER, 0, numLock = true)
        assertEquals(KeyOutput.Sequence("\n"), encode(enter))
        assertEquals(KeyOutput.Sequence("\u001bOM"), encode(enter, keypadApp = true))
    }
}
