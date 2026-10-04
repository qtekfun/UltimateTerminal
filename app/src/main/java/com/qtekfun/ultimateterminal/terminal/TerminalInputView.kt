// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.qtekfun.ultimateterminal.domain.terminal.ComposingText
import com.qtekfun.ultimateterminal.domain.terminal.CompositionEdit
import com.qtekfun.ultimateterminal.domain.terminal.KeyInput

/**
 * An invisible view that receives the keyboard for the terminal: hardware keys, and the soft
 * keyboard through an [InputConnection]. The screen itself is drawn in Compose; this view only
 * exists because Android delivers text input to a `View`.
 *
 * The soft keyboard is asked for plain, non-suggesting input, but Gboard still composes words and
 * only commits them when the keyboard is hidden (found on a Pixel 8: what was typed did not reach
 * the shell until then). So composing text is sent as it changes, see [ComposingText].
 */
class TerminalInputView(context: Context) : View(context) {
    var sink: TerminalInputSink? = null

    private val composing = ComposingText()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // The system adds affordances to a focused editor, anchored at its origin (the top-left
        // corner of the terminal): the autofill suggestions and, from Android 14, the stylus
        // handwriting icon. A terminal wants neither: a dark round "⋮" showed over the first
        // characters on a Pixel 8, and nothing in this app draws it. Not confirmed on a device.
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setAutoHandwritingEnabled(false)
        }
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
        outAttrs.imeOptions =
            EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        composing.finish()
        return TerminalInputConnection()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        sink?.onKey(keyInputOf(event)) ?: super.onKeyDown(keyCode, event)

    private inner class TerminalInputConnection : BaseInputConnection(this, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            send(composing.update(text?.toString().orEmpty()))
            composing.finish()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            send(composing.update(text?.toString().orEmpty()))
            return true
        }

        // What was composed has already reached the shell: only the next word starts over.
        override fun finishComposingText(): Boolean {
            composing.finish()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength > 0) {
                sink?.onDeleteBefore(composing.deleteBefore(beforeLength))
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            return sink?.onKey(keyInputOf(event)) ?: false
        }
    }

    private fun send(edit: CompositionEdit) {
        if (edit.deleteCount > 0) sink?.onDeleteBefore(edit.deleteCount)
        if (edit.insert.isNotEmpty()) sink?.onText(edit.insert)
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
