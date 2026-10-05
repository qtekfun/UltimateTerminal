// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyboardTypeTest {
    @Test
    fun theConstantsAreThoseOfAndroid() {
        assertEquals(InputType.TYPE_NULL, KeyboardFlags.TYPE_NULL)
        assertEquals(InputType.TYPE_CLASS_TEXT, KeyboardFlags.TYPE_CLASS_TEXT)
        assertEquals(
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            KeyboardFlags.TYPE_TEXT_VARIATION_COMPAT
        )
        assertEquals(
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            KeyboardFlags.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        assertEquals(EditorInfo.IME_FLAG_NO_FULLSCREEN, KeyboardFlags.IME_FLAG_NO_FULLSCREEN)
        assertEquals(EditorInfo.IME_FLAG_NO_EXTRACT_UI, KeyboardFlags.IME_FLAG_NO_EXTRACT_UI)
        assertEquals(
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            KeyboardFlags.IME_FLAG_NO_PERSONALIZED_LEARNING
        )
    }

    @Test
    fun theNormalKeyboardIsAPlainTextFieldWithoutSuggestionsOrPasswordVariation() {
        val type = KeyboardFlags.of(KeyboardType.NORMAL).inputType

        assertEquals(0x80001, type)
        assertEquals(InputType.TYPE_CLASS_TEXT, type and InputType.TYPE_MASK_CLASS)
        assertEquals(0, type and InputType.TYPE_MASK_VARIATION)
        assertTrue(type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        assertFalse(type and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0)
    }

    @Test
    fun theCompatibleKeyboardIsAVisiblePasswordField() {
        val type = KeyboardFlags.of(KeyboardType.COMPATIBLE).inputType

        assertEquals(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            type
        )
    }

    @Test
    fun theRawKeyboardHasNoInputType() {
        assertEquals(InputType.TYPE_NULL, KeyboardFlags.of(KeyboardType.RAW).inputType)
    }

    @Test
    fun everyTypeKeepsTheImeOptionsOfTheTerminal() {
        val expected = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        KeyboardType.entries.forEach {
            assertEquals(expected, KeyboardFlags.of(it).imeOptions, it.name)
        }
    }

    @Test
    fun theNormalKeyboardIsTheDefaultAndUnknownNamesFallBackToIt() {
        assertEquals(KeyboardType.NORMAL, KeyboardType.DEFAULT)
        assertEquals(KeyboardType.RAW, KeyboardType.parse("RAW"))
        assertEquals(KeyboardType.COMPATIBLE, KeyboardType.parse("COMPATIBLE"))
        assertEquals(KeyboardType.DEFAULT, KeyboardType.parse("secure"))
        assertEquals(KeyboardType.DEFAULT, KeyboardType.parse(null))
    }
}
