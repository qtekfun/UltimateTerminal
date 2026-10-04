// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent

/** What an application shortcut asks for: the clipboard, the font size, the tabs and the panes. */
sealed interface AppShortcut {
    val id: String

    data object NewTab : AppShortcut {
        override val id = "new_tab"
    }

    data object CloseTab : AppShortcut {
        override val id = "close_tab"
    }

    data object NextTab : AppShortcut {
        override val id = "next_tab"
    }

    data object PreviousTab : AppShortcut {
        override val id = "previous_tab"
    }

    /** Selects tab [number], counting from 1. */
    data class SelectTab(val number: Int) : AppShortcut {
        init {
            require(number in 1..MAX_DIRECT_TAB) { "tab number out of range: $number" }
        }

        override val id = "select_tab_$number"
    }

    data object SplitHorizontal : AppShortcut {
        override val id = "split_horizontal"
    }

    data object SplitVertical : AppShortcut {
        override val id = "split_vertical"
    }

    data object ClosePane : AppShortcut {
        override val id = "close_pane"
    }

    data object ToggleZoom : AppShortcut {
        override val id = "toggle_pane_zoom"
    }

    data object FocusLeft : AppShortcut {
        override val id = "focus_pane_left"
    }

    data object FocusRight : AppShortcut {
        override val id = "focus_pane_right"
    }

    data object FocusUp : AppShortcut {
        override val id = "focus_pane_up"
    }

    data object FocusDown : AppShortcut {
        override val id = "focus_pane_down"
    }

    /** Turns on or off the typing in every pane at once (SPEC RF-12). */
    data object ToggleBroadcast : AppShortcut {
        override val id = "toggle_broadcast"
    }

    /** Saves the panes of the tab as a named layout. */
    data object SaveLayout : AppShortcut {
        override val id = "save_layout"
    }

    /** Opens the list of saved layouts. */
    data object OpenLayouts : AppShortcut {
        override val id = "open_layouts"
    }

    data object Copy : AppShortcut {
        override val id = "copy"
    }

    data object Paste : AppShortcut {
        override val id = "paste"
    }

    data object ZoomIn : AppShortcut {
        override val id = "zoom_in"
    }

    data object ZoomOut : AppShortcut {
        override val id = "zoom_out"
    }

    data object ZoomReset : AppShortcut {
        override val id = "zoom_reset"
    }

    companion object {
        const val MAX_DIRECT_TAB = 9

        private val fixed = listOf(
            NewTab, CloseTab, NextTab, PreviousTab, Copy, Paste, ZoomIn, ZoomOut, ZoomReset,
            SplitHorizontal, SplitVertical, ClosePane, ToggleZoom,
            FocusLeft, FocusRight, FocusUp, FocusDown,
            ToggleBroadcast, SaveLayout, OpenLayouts
        )
        private val byId: Map<String, AppShortcut> =
            (fixed + (1..MAX_DIRECT_TAB).map { SelectTab(it) }).associateBy { it.id }

        fun fromId(id: String): AppShortcut? = byId[id]
    }
}

/** A key together with the modifiers that must be held. All three must match exactly. */
data class KeyChord(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false
) {
    /**
     * A chord may not steal ordinary typing: it needs Ctrl or Alt (Shift alone only makes capitals),
     * the one exception being Shift+Insert, the classic paste.
     */
    val isUsable: Boolean
        get() = ctrl || alt || (shift && keyCode == KeyEvent.KEYCODE_INSERT)

    /** Text such as `ctrl+shift+t`; modifiers in a fixed order, then the key name. */
    fun format(): String? {
        val key = KeyNames.nameOf(keyCode) ?: return null
        return buildList {
            if (ctrl) add("ctrl")
            if (alt) add("alt")
            if (shift) add("shift")
            add(key)
        }.joinToString("+")
    }

    companion object {
        fun parse(text: String): KeyChord? {
            val parts = text.trim().lowercase().split('+')
            val modifiers = parts.dropLast(1).toSet()
            val keyCode = parts.lastOrNull()?.let(KeyNames::codeOf)
            val known = modifiers.all { it in setOf("ctrl", "alt", "shift") }
            return if (keyCode == null || !known || modifiers.size != parts.size - 1) {
                null
            } else {
                KeyChord(keyCode, "ctrl" in modifiers, "alt" in modifiers, "shift" in modifiers)
            }
        }
    }
}

/** The keys that can appear in a shortcut, by the name used in the stored form. */
object KeyNames {
    private val names: Map<String, Int> = buildMap {
        ('a'..'z').forEachIndexed { index, letter ->
            put(letter.toString(), KeyEvent.KEYCODE_A + index)
        }
        ('0'..'9').forEachIndexed { index, digit ->
            put(digit.toString(), KeyEvent.KEYCODE_0 + index)
        }
        for (number in 1..FUNCTION_KEYS) put("f$number", KeyEvent.KEYCODE_F1 + number - 1)
        put("tab", KeyEvent.KEYCODE_TAB)
        put("insert", KeyEvent.KEYCODE_INSERT)
        put("equals", KeyEvent.KEYCODE_EQUALS)
        put("minus", KeyEvent.KEYCODE_MINUS)
        put("plus", KeyEvent.KEYCODE_PLUS)
        put("numpad_add", KeyEvent.KEYCODE_NUMPAD_ADD)
        put("numpad_subtract", KeyEvent.KEYCODE_NUMPAD_SUBTRACT)
        put("page_up", KeyEvent.KEYCODE_PAGE_UP)
        put("page_down", KeyEvent.KEYCODE_PAGE_DOWN)
        put("left", KeyEvent.KEYCODE_DPAD_LEFT)
        put("right", KeyEvent.KEYCODE_DPAD_RIGHT)
        put("up", KeyEvent.KEYCODE_DPAD_UP)
        put("down", KeyEvent.KEYCODE_DPAD_DOWN)
    }
    private val codes = names.entries.associate { it.value to it.key }

    private const val FUNCTION_KEYS = 12

    fun codeOf(name: String): Int? = names[name]

    fun nameOf(keyCode: Int): String? = codes[keyCode]
}

/** The result of reading a stored shortcut map: the map, and the lines that could not be used. */
data class ShortcutParse(val map: ShortcutMap, val rejected: List<RejectedLine>)

/**
 * The application shortcuts: which chord triggers what. Immutable; changing one returns a new map.
 * Stored as text, one `chord=shortcut` per line.
 */
class ShortcutMap private constructor(private val bindings: Map<KeyChord, AppShortcut>) {
    val all: Map<KeyChord, AppShortcut> get() = bindings

    /** The shortcut that [input] triggers, if any. Meta is ignored. */
    fun match(input: KeyInput): AppShortcut? =
        bindings[KeyChord(input.keyCode, input.ctrl, input.alt, input.shift)]

    /** Binds [chord] to [shortcut]; fails on a chord that would steal ordinary typing. */
    fun bind(chord: KeyChord, shortcut: AppShortcut): ShortcutMap {
        require(chord.isUsable) { "a shortcut needs Ctrl or Alt" }
        return ShortcutMap(bindings + (chord to shortcut))
    }

    fun unbind(chord: KeyChord): ShortcutMap = ShortcutMap(bindings - chord)

    fun serialize(): String = bindings.entries
        .mapNotNull { (chord, shortcut) -> chord.format()?.let { "$it=${shortcut.id}" } }
        .sorted()
        .joinToString("\n", postfix = "\n")

    companion object {
        /**
         * Shortcuts that rarely clash with programs. Alt+digit does clash with readline's numeric
         * arguments (the specification asks for it; it can be rebound), so tab selection is the
         * one default worth reviewing.
         */
        fun defaults(): ShortcutMap {
            val shift = true
            val map = mutableMapOf(
                KeyChord(KeyEvent.KEYCODE_T, ctrl = true, shift = shift) to AppShortcut.NewTab,
                KeyChord(KeyEvent.KEYCODE_W, ctrl = true, shift = shift) to AppShortcut.CloseTab,
                KeyChord(KeyEvent.KEYCODE_TAB, ctrl = true) to AppShortcut.NextTab,
                KeyChord(KeyEvent.KEYCODE_TAB, ctrl = true, shift = shift) to
                    AppShortcut.PreviousTab,
                KeyChord(KeyEvent.KEYCODE_C, ctrl = true, shift = shift) to AppShortcut.Copy,
                KeyChord(KeyEvent.KEYCODE_INSERT, ctrl = true) to AppShortcut.Copy,
                KeyChord(KeyEvent.KEYCODE_V, ctrl = true, shift = shift) to AppShortcut.Paste,
                KeyChord(KeyEvent.KEYCODE_INSERT, shift = shift) to AppShortcut.Paste,
                KeyChord(KeyEvent.KEYCODE_EQUALS, ctrl = true, shift = shift) to
                    AppShortcut.ZoomIn,
                KeyChord(KeyEvent.KEYCODE_PLUS, ctrl = true, shift = shift) to AppShortcut.ZoomIn,
                KeyChord(KeyEvent.KEYCODE_NUMPAD_ADD, ctrl = true) to AppShortcut.ZoomIn,
                KeyChord(KeyEvent.KEYCODE_MINUS, ctrl = true, shift = shift) to
                    AppShortcut.ZoomOut,
                KeyChord(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, ctrl = true) to AppShortcut.ZoomOut,
                KeyChord(KeyEvent.KEYCODE_0, ctrl = true, shift = shift) to AppShortcut.ZoomReset,
                // Panes, as Terminator has them where the keys are free here. Ctrl+Shift+W already
                // closes the tab, so the pane takes Q. The focus keys use Ctrl+Alt so that Alt+arrow
                // keeps moving by words in readline.
                KeyChord(KeyEvent.KEYCODE_O, ctrl = true, shift = shift) to
                    AppShortcut.SplitHorizontal,
                KeyChord(KeyEvent.KEYCODE_E, ctrl = true, shift = shift) to
                    AppShortcut.SplitVertical,
                KeyChord(KeyEvent.KEYCODE_Q, ctrl = true, shift = shift) to AppShortcut.ClosePane,
                KeyChord(KeyEvent.KEYCODE_X, ctrl = true, shift = shift) to AppShortcut.ToggleZoom,
                KeyChord(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true, alt = true) to
                    AppShortcut.FocusLeft,
                KeyChord(KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = true, alt = true) to
                    AppShortcut.FocusRight,
                KeyChord(KeyEvent.KEYCODE_DPAD_UP, ctrl = true, alt = true) to AppShortcut.FocusUp,
                KeyChord(KeyEvent.KEYCODE_DPAD_DOWN, ctrl = true, alt = true) to
                    AppShortcut.FocusDown,
                // Layouts and broadcast. Terminator uses Alt+letter for these; Alt+letter is readline's
                // Meta (Alt+b and Alt+f move by words), so they take Ctrl+Shift instead.
                KeyChord(KeyEvent.KEYCODE_B, ctrl = true, shift = shift) to
                    AppShortcut.ToggleBroadcast,
                KeyChord(KeyEvent.KEYCODE_S, ctrl = true, shift = shift) to
                    AppShortcut.SaveLayout,
                KeyChord(KeyEvent.KEYCODE_L, ctrl = true, shift = shift) to
                    AppShortcut.OpenLayouts
            )
            for (number in 1..AppShortcut.MAX_DIRECT_TAB) {
                map[KeyChord(KeyEvent.KEYCODE_0 + number, alt = true)] =
                    AppShortcut.SelectTab(number)
            }
            return ShortcutMap(map)
        }

        /** Reads text written by [serialize]; lines that make no sense are skipped and reported. */
        fun parse(text: String): ShortcutParse {
            val bindings = mutableMapOf<KeyChord, AppShortcut>()
            val rejected = mutableListOf<RejectedLine>()
            text.lineSequence().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    val reason = bindLine(line, bindings)
                    if (reason != null) rejected += RejectedLine(line, reason)
                }
            return ShortcutParse(ShortcutMap(bindings), rejected)
        }

        /** Returns why [line] was not used, or null if it was added to [bindings]. */
        private fun bindLine(line: String, bindings: MutableMap<KeyChord, AppShortcut>): String? {
            val chord = KeyChord.parse(line.substringBefore('='))
            val shortcut = AppShortcut.fromId(line.substringAfter('=', "").trim())
            val reason = when {
                '=' !in line -> "missing ="
                chord == null -> "unknown key"
                !chord.isUsable -> "needs Ctrl or Alt"
                shortcut == null -> "unknown shortcut"
                else -> null
            }
            if (reason == null && chord != null && shortcut != null) bindings[chord] = shortcut
            return reason
        }
    }
}
