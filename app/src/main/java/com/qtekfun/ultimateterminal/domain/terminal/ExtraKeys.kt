// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent

/** What an extra key does when tapped. */
sealed interface ExtraKeyAction {
    /** Sends a key press, with the sticky modifiers applied. */
    data class Press(val keyCode: Int, val unicode: Int = 0) : ExtraKeyAction

    /** Sends literal characters, with the sticky modifiers applied if it is a single one. */
    data class Type(val text: String) : ExtraKeyAction

    /** Toggles a sticky modifier. */
    data class Modifier(val key: StickyKey) : ExtraKeyAction
}

/**
 * A key of the extra-keys row. [symbol] is what is drawn (a conventional abbreviation or glyph, the
 * same in every language); the localized spoken name is looked up by [id] in the UI.
 */
data class ExtraKey(val id: String, val symbol: String, val action: ExtraKeyAction)

/** Every key the extra-keys row can show, by id. */
object ExtraKeyCatalog {
    private fun press(id: String, symbol: String, keyCode: Int) =
        ExtraKey(id, symbol, ExtraKeyAction.Press(keyCode))

    private fun typed(id: String, text: String) = ExtraKey(id, text, ExtraKeyAction.Type(text))

    val all: List<ExtraKey> = listOf(
        press("esc", "ESC", KeyEvent.KEYCODE_ESCAPE),
        press("tab", "TAB", KeyEvent.KEYCODE_TAB),
        ExtraKey("ctrl", "CTRL", ExtraKeyAction.Modifier(StickyKey.CTRL)),
        ExtraKey("alt", "ALT", ExtraKeyAction.Modifier(StickyKey.ALT)),
        press("up", "↑", KeyEvent.KEYCODE_DPAD_UP),
        press("down", "↓", KeyEvent.KEYCODE_DPAD_DOWN),
        press("left", "←", KeyEvent.KEYCODE_DPAD_LEFT),
        press("right", "→", KeyEvent.KEYCODE_DPAD_RIGHT),
        press("home", "HOME", KeyEvent.KEYCODE_MOVE_HOME),
        press("end", "END", KeyEvent.KEYCODE_MOVE_END),
        press("pgup", "PGUP", KeyEvent.KEYCODE_PAGE_UP),
        press("pgdn", "PGDN", KeyEvent.KEYCODE_PAGE_DOWN),
        press("ins", "INS", KeyEvent.KEYCODE_INSERT),
        press("del", "DEL", KeyEvent.KEYCODE_FORWARD_DEL),
        typed("slash", "/"),
        typed("dash", "-"),
        typed("pipe", "|"),
        typed("tilde", "~"),
        typed("backslash", "\\")
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): ExtraKey? = byId[id]
}

/** The reason a line of a stored extra-keys configuration was not used. */
data class RejectedLine(val line: String, val reason: String)

/**
 * The extra-keys row: which keys, in which rows. Rows hold catalog ids, so the stored form stays
 * small and survives changes to how a key is sent. [visible] hides the whole row (a hardware
 * keyboard user may not want it). With [onlyWithKeyboard] (the default) the row is shown only
 * while the on-screen keyboard is, so it does not take terminal space when there is nothing to
 * type with; see [shownWith].
 */
data class ExtraKeysConfig(
    val rows: List<List<String>>,
    val visible: Boolean = true,
    val onlyWithKeyboard: Boolean = true
) {
    /** This configuration as it must be drawn now: hidden when it follows a keyboard that is not up. */
    fun shownWith(keyboardVisible: Boolean): ExtraKeysConfig =
        if (onlyWithKeyboard && !keyboardVisible) copy(visible = false) else this

    /** The keys of each row; ids that are not in the catalog are skipped. */
    fun resolved(): List<List<ExtraKey>> = rows
        .map { row -> row.mapNotNull(ExtraKeyCatalog::find) }
        .filter { it.isNotEmpty() }

    /**
     * Plain text, one row per line with ids separated by spaces, after an optional `visible` line:
     * easy to store, diff and edit by hand.
     */
    fun serialize(): String = buildString {
        append(VISIBLE_PREFIX).append(visible).append('\n')
        append(ONLY_WITH_KEYBOARD_PREFIX).append(onlyWithKeyboard).append('\n')
        rows.forEach { row -> append(row.joinToString(" ")).append('\n') }
    }

    companion object {
        private const val VISIBLE_PREFIX = "visible="
        private const val ONLY_WITH_KEYBOARD_PREFIX = "onlyWithKeyboard="

        /** Two rows of seven keys, which fit a 360 dp wide phone at the 48 dp touch size. */
        fun default() = ExtraKeysConfig(
            rows = listOf(
                listOf("esc", "slash", "dash", "home", "up", "end", "pgup"),
                listOf("tab", "ctrl", "alt", "left", "down", "right", "pgdn")
            )
        )

        /**
         * Reads [text] as written by [serialize]. Lenient: unknown ids and blank lines are dropped,
         * and the result falls back to [default] if nothing usable is left. The ids that were
         * dropped are reported in the second value.
         */
        fun parse(text: String): Pair<ExtraKeysConfig, List<RejectedLine>> {
            val rejected = mutableListOf<RejectedLine>()
            var visible = true
            var onlyWithKeyboard = true
            val rows = mutableListOf<List<String>>()
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                if (line.startsWith(VISIBLE_PREFIX)) {
                    visible = line.removePrefix(VISIBLE_PREFIX) != "false"
                } else if (line.startsWith(ONLY_WITH_KEYBOARD_PREFIX)) {
                    onlyWithKeyboard = line.removePrefix(ONLY_WITH_KEYBOARD_PREFIX) != "false"
                } else {
                    val ids = line.split(' ').filter { it.isNotEmpty() }
                    ids.filter { ExtraKeyCatalog.find(it) == null }
                        .forEach { rejected += RejectedLine(it, "unknown key") }
                    ids.filter { ExtraKeyCatalog.find(it) != null }
                        .takeIf { it.isNotEmpty() }
                        ?.let { rows += it }
                }
            }
            val config = if (rows.isEmpty()) {
                default().copy(visible = visible, onlyWithKeyboard = onlyWithKeyboard)
            } else {
                ExtraKeysConfig(rows, visible, onlyWithKeyboard)
            }
            return config to rejected
        }
    }
}

/** Where the extra-keys configuration is kept. The implementation (settings, Room) is not here. */
interface ExtraKeysStore {
    suspend fun load(): ExtraKeysConfig

    suspend fun save(config: ExtraKeysConfig)
}
