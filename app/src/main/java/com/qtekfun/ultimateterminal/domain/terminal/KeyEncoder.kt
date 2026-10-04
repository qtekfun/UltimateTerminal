// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import com.termux.terminal.KeyHandler

/** A key press, reduced to what the terminal needs. [unicode] is 0 for keys that type nothing. */
data class KeyInput(
    val keyCode: Int,
    val unicode: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val numLock: Boolean = false
)

/** What to send to the shell for a key press. */
sealed interface KeyOutput {
    /** A ready-made escape sequence or control character. */
    data class Sequence(val text: String) : KeyOutput

    /** A character, optionally preceded by ESC (that is how Alt is sent). */
    data class CodePoint(val value: Int, val escapePrefix: Boolean) : KeyOutput

    /** Nothing to send (a modifier key on its own, for instance). */
    data object None : KeyOutput
}

object KeyEncoder {
    private const val NUL = 0
    private const val ESC = 27
    private const val FS = 28
    private const val GS = 29
    private const val RS = 30
    private const val US = 31
    private const val DEL = 127

    fun encode(
        input: KeyInput,
        cursorKeysApplication: Boolean,
        keypadApplication: Boolean
    ): KeyOutput {
        val modifiers = modifiersOf(input)
        val sequence = KeyHandler.getCode(
            input.keyCode,
            modifiers,
            cursorKeysApplication,
            keypadApplication
        )
        return when {
            sequence != null -> KeyOutput.Sequence(sequence)
            input.unicode <= 0 -> KeyOutput.None
            input.ctrl -> KeyOutput.CodePoint(controlCharacter(input.unicode), input.alt)
            else -> KeyOutput.CodePoint(input.unicode, input.alt)
        }
    }

    private fun modifiersOf(input: KeyInput): Int {
        var modifiers = 0
        if (input.alt) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        if (input.ctrl) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (input.shift) modifiers = modifiers or KeyHandler.KEYMOD_SHIFT
        if (input.numLock) modifiers = modifiers or KeyHandler.KEYMOD_NUM_LOCK
        return modifiers
    }

    /** Ctrl+letter and the classic punctuation combinations, as a terminal expects them. */
    private fun controlCharacter(codePoint: Int): Int = when (codePoint) {
        in 'a'.code..'z'.code -> codePoint - 'a'.code + 1
        in 'A'.code..'Z'.code -> codePoint - 'A'.code + 1
        ' '.code, '2'.code, '@'.code -> NUL
        '['.code, '3'.code -> ESC
        '\\'.code, '4'.code -> FS
        ']'.code, '5'.code -> GS
        '^'.code, '6'.code -> RS
        '_'.code, '7'.code, '/'.code -> US
        '8'.code, '?'.code -> DEL
        else -> codePoint
    }
}
