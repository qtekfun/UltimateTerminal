// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.qtekfun.ultimateterminal.domain.terminal.KeyInput

/**
 * An invisible view that receives the keyboard for the terminal: hardware keys, and the soft
 * keyboard through an [InputConnection]. The screen itself is drawn in Compose; this view only
 * exists because Android delivers text input to a `View`.
 *
 * The soft keyboard is asked for plain, non-suggesting input so that it sends characters as they
 * are typed instead of composing words. Not validated on a device yet (see DECISIONS.md, T03).
 */
class TerminalInputView(context: Context) : View(context) {
    var sink: TerminalInputSink? = null

    private val composing = StringBuilder()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /** Gives this view the keyboard focus and opens the soft keyboard. */
    fun showKeyboard() {
        requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        composing.clear()
        return TerminalInputConnection()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        sink?.onKey(keyInputOf(event)) ?: super.onKeyDown(keyCode, event)

    private inner class TerminalInputConnection : BaseInputConnection(this, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing.clear()
            if (!text.isNullOrEmpty()) sink?.onText(text.toString())
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing.clear()
            composing.append(text)
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composing.isNotEmpty()) sink?.onText(composing.toString())
            composing.clear()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (composing.isNotEmpty()) {
                composing.clear()
            } else if (beforeLength > 0) {
                sink?.onDeleteBefore(beforeLength)
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            return sink?.onKey(keyInputOf(event)) ?: false
        }
    }

    private fun keyInputOf(event: KeyEvent): KeyInput {
        val withoutControlKeys = event.metaState and
            (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK).inv()
        val unicode = event.getUnicodeChar(withoutControlKeys)
        return KeyInput(
            keyCode = event.keyCode,
            // A dead key (accent waiting for its letter) types nothing by itself.
            unicode = if (unicode and KeyCharacterMap.COMBINING_ACCENT != 0) 0 else unicode,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed,
            numLock = event.isNumLockOn
        )
    }
}
