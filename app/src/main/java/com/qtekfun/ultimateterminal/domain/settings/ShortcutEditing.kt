// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import android.view.KeyEvent
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.BindResult
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutConflict
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import com.qtekfun.ultimateterminal.domain.terminal.bindChecked

/** What giving an action the key combination the user typed would do, shown before they confirm. */
sealed interface ShortcutPreview {
    /** Nothing typed yet. */
    data object Empty : ShortcutPreview

    /** The text is not a combination (`ctrl+shift+t`): an unknown key or modifier. */
    data object Unreadable : ShortcutPreview

    /** A combination without Ctrl or Alt would steal ordinary typing. */
    data object NeedsModifier : ShortcutPreview

    /**
     * It can be added. [replaced] is the other action that has the combination now (it loses it),
     * [conflict] what it costs a program in the terminal (a warning, never a refusal) and
     * [alreadyBound] says the action already has this very combination, so adding does nothing.
     */
    data class Ready(
        val chord: KeyChord,
        val replaced: AppShortcut?,
        val conflict: ShortcutConflict?,
        val alreadyBound: Boolean
    ) : ShortcutPreview
}

/** The edits Settings makes to the shortcuts. Pure: each returns a new map and touches nothing. */
object ShortcutEditing {
    /** The combinations of [shortcut] in [map], in the stored order of their names. */
    fun chordsOf(map: ShortcutMap, shortcut: AppShortcut): List<KeyChord> =
        map.all.filterValues { it == shortcut }.keys.sortedBy { it.format().orEmpty() }

    /** What adding the combination in [text] to [shortcut] would do. */
    fun preview(map: ShortcutMap, shortcut: AppShortcut, text: String): ShortcutPreview {
        val chord = KeyChord.parse(text)
        return when {
            text.isBlank() -> ShortcutPreview.Empty

            chord == null -> ShortcutPreview.Unreadable

            else -> when (val result = map.bindChecked(chord, shortcut)) {
                is BindResult.Refused -> ShortcutPreview.NeedsModifier

                is BindResult.Bound -> ShortcutPreview.Ready(
                    chord,
                    result.replaced,
                    result.conflict,
                    alreadyBound = map.all[chord] == shortcut
                )
            }
        }
    }

    /** [map] with the combination of [text] given to [shortcut]; unchanged if it cannot be added. */
    fun add(map: ShortcutMap, shortcut: AppShortcut, text: String): ShortcutMap =
        if (preview(map, shortcut, text) is ShortcutPreview.Ready) {
            map.bind(requireNotNull(KeyChord.parse(text)), shortcut)
        } else {
            map
        }

    fun remove(map: ShortcutMap, chord: KeyChord): ShortcutMap = map.unbind(chord)

    /**
     * The combination a key press makes, as text the field shows (`ctrl+alt+k`), or null for a
     * modifier on its own and for a key that has no name in the stored form: the user is still
     * holding the modifiers and has not pressed the key yet.
     */
    fun captured(keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): String? =
        if (keyCode in MODIFIER_KEYS) null else KeyChord(keyCode, ctrl, alt, shift).format()

    private val MODIFIER_KEYS = setOf(
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT
    )
}
