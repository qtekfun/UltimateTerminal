// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/**
 * The kind of text field the soft keyboard is told the terminal is (SPEC RF-08). Some keyboards,
 * above all the ones that ship with Chinese phones, replace themselves with a secure keyboard when
 * a field looks like a password, so the default is the one that does not.
 */
enum class KeyboardType {
    /** A plain text field that does not suggest or correct: the keyboard of the phone as usual. */
    NORMAL,

    /**
     * A visible-password field: what some stock keyboards need to stop correcting and composing on
     * a terminal. The secure keyboard of some manufacturers appears with it.
     */
    COMPATIBLE,

    /** No input type at all, as Termux's `input-type` option does: plain key events. */
    RAW;

    companion object {
        val DEFAULT = NORMAL

        /** The type with that stored name; anything else (a missing or a later value) is [DEFAULT]. */
        fun parse(name: String?): KeyboardType = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** The `EditorInfo` values of a [KeyboardType]; kept apart from the view so they can be tested. */
data class EditorFlags(val inputType: Int, val imeOptions: Int)

/**
 * Maps a [KeyboardType] to what `onCreateInputConnection` puts in `EditorInfo`. The numbers are
 * those of `android.text.InputType` and `android.view.inputmethod.EditorInfo`, written here so no
 * Android class is needed; a test checks them against the platform's constants.
 */
object KeyboardFlags {
    const val TYPE_NULL = 0
    const val TYPE_CLASS_TEXT = 0x1

    /** The text input variation with value 0x90 that the old terminal field used (see [KeyboardType.COMPATIBLE]). */
    const val TYPE_TEXT_VARIATION_COMPAT = 0x90

    const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x80000
    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
    const val IME_FLAG_NO_FULLSCREEN = 0x2000000
    const val IME_FLAG_NO_EXTRACT_UI = 0x10000000

    private const val IME_OPTIONS =
        IME_FLAG_NO_FULLSCREEN or IME_FLAG_NO_EXTRACT_UI or IME_FLAG_NO_PERSONALIZED_LEARNING

    fun of(type: KeyboardType): EditorFlags = EditorFlags(
        inputType = when (type) {
            KeyboardType.NORMAL -> TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS

            KeyboardType.COMPATIBLE ->
                TYPE_CLASS_TEXT or
                    TYPE_TEXT_VARIATION_COMPAT or TYPE_TEXT_FLAG_NO_SUGGESTIONS

            KeyboardType.RAW -> TYPE_NULL
        },
        imeOptions = IME_OPTIONS
    )
}
