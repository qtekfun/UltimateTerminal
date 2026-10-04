// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent

/** What the keyboard input became. */
sealed interface RoutedInput {
    /** An application shortcut (copy, new tab...). Nothing is sent to the shell. */
    data class Shortcut(val shortcut: AppShortcut) : RoutedInput

    /** A key press for the shell, with the sticky modifiers already merged in. */
    data class Key(val input: KeyInput) : RoutedInput

    /** Characters for the shell, exactly as typed. */
    data class Text(val text: String) : RoutedInput

    /** Nothing: a modifier key on its own, or a tap on a sticky key. */
    data object Ignored : RoutedInput
}

/**
 * Decides what each keyboard input means: an application shortcut, a key for the shell, or
 * nothing. It also owns the sticky modifiers of the extra-keys row, which apply to hardware keys,
 * soft-keyboard text and extra keys alike (so tapping CTRL and then typing `c` on the soft
 * keyboard sends Ctrl+C).
 */
class InputRouter(
    var shortcuts: ShortcutMap = ShortcutMap.defaults(),
    private val onStickyChanged: (StickyState) -> Unit = {}
) {
    var sticky: StickyState = StickyState()
        private set

    private fun setSticky(state: StickyState) {
        if (state != sticky) {
            sticky = state
            onStickyChanged(state)
        }
    }

    fun route(input: KeyInput): RoutedInput {
        if (input.keyCode in MODIFIER_KEYS) return RoutedInput.Ignored
        val merged = input.copy(
            ctrl = input.ctrl || sticky.ctrlActive,
            alt = input.alt || sticky.altActive
        )
        setSticky(sticky.afterKey())
        val shortcut = shortcuts.match(merged)
        return if (shortcut != null) RoutedInput.Shortcut(shortcut) else RoutedInput.Key(merged)
    }

    fun routeText(text: String): RoutedInput = when {
        text.isEmpty() -> RoutedInput.Ignored

        sticky.anyActive && text.codePointCount(0, text.length) == 1 ->
            route(KeyInput(KeyEvent.KEYCODE_UNKNOWN, text.codePointAt(0)))

        else -> {
            setSticky(sticky.afterKey())
            RoutedInput.Text(text)
        }
    }

    fun press(key: ExtraKey): RoutedInput = when (val action = key.action) {
        is ExtraKeyAction.Press -> route(KeyInput(action.keyCode, action.unicode))

        is ExtraKeyAction.Type -> routeText(action.text)

        is ExtraKeyAction.Modifier -> {
            setSticky(sticky.tap(action.key))
            RoutedInput.Ignored
        }
    }

    private companion object {
        val MODIFIER_KEYS = setOf(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_CAPS_LOCK,
            KeyEvent.KEYCODE_NUM_LOCK,
            KeyEvent.KEYCODE_SCROLL_LOCK,
            KeyEvent.KEYCODE_FUNCTION,
            KeyEvent.KEYCODE_SYM
        )
    }
}
