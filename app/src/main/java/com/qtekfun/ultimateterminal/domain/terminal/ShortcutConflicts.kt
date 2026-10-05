// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

import android.view.KeyEvent

/**
 * A shortcut that works but takes a key a program running in the terminal may need (SPEC RF-12).
 * These are warnings the settings screen shows, never refusals: the specification itself asks for
 * Alt+digit to select a tab, which readline uses for numeric arguments.
 */
sealed interface ShortcutConflict {
    val chord: KeyChord

    /** Ctrl plus a letter, alone, is a control character: Ctrl+C interrupts, Ctrl+D ends input. */
    data class StealsControlKey(override val chord: KeyChord) : ShortcutConflict

    /** Alt plus a letter or digit, without Ctrl, is readline's Meta: word moves, numeric arguments. */
    data class StealsReadlineMeta(override val chord: KeyChord) : ShortcutConflict

    /** The text gave [chord] to two actions: [kept] won (the later line), [ignored] did not. */
    data class DuplicateInText(
        override val chord: KeyChord,
        val kept: AppShortcut,
        val ignored: AppShortcut
    ) : ShortcutConflict
}

private val LETTERS = KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
private val DIGITS = KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9

/** What taking this chord costs a program in the terminal, or null if nothing common. */
fun KeyChord.terminalConflict(): ShortcutConflict? = when {
    ctrl && !alt && !shift && keyCode in LETTERS -> ShortcutConflict.StealsControlKey(this)

    alt && !ctrl && (keyCode in LETTERS || keyCode in DIGITS) ->
        ShortcutConflict.StealsReadlineMeta(this)

    else -> null
}

/** The costs of every chord of the map, in the order of their stored form so it is stable. */
fun ShortcutMap.conflicts(): List<ShortcutConflict> = all.keys
    .sortedBy { it.format().orEmpty() }
    .mapNotNull { it.terminalConflict() }

/** Why a chord was not bound. */
enum class BindRefusal { NEEDS_CTRL_OR_ALT }

/** The outcome of binding a chord: the new map, what it replaced and what it costs. */
sealed interface BindResult {
    /** [replaced] is the action the chord had before, null if it was free. */
    data class Bound(
        val map: ShortcutMap,
        val replaced: AppShortcut?,
        val conflict: ShortcutConflict?
    ) : BindResult

    data class Refused(val reason: BindRefusal) : BindResult
}

/** Binds like [ShortcutMap.bind], but reports instead of throwing, so a screen can warn and ask. */
fun ShortcutMap.bindChecked(chord: KeyChord, shortcut: AppShortcut): BindResult =
    if (chord.isUsable) {
        BindResult.Bound(
            bind(chord, shortcut),
            all[chord]?.takeIf {
                it != shortcut
            },
            chord.terminalConflict()
        )
    } else {
        BindResult.Refused(BindRefusal.NEEDS_CTRL_OR_ALT)
    }

/** A shortcut map read from text, with what [ShortcutMap.parse] could not say about it. */
data class ShortcutAnalysis(
    val map: ShortcutMap,
    val rejected: List<RejectedLine>,
    val duplicates: List<ShortcutConflict.DuplicateInText>
)

/** Reads stored shortcuts and also finds a chord that the text gave to two different actions. */
object ShortcutText {
    fun analyze(text: String): ShortcutAnalysis {
        val parsed = ShortcutMap.parse(text)
        val bound = mutableMapOf<KeyChord, AppShortcut>()
        val duplicates = mutableListOf<ShortcutConflict.DuplicateInText>()
        text.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val chord = KeyChord.parse(line.substringBefore('='))
                val shortcut = AppShortcut.fromId(line.substringAfter('=', "").trim())
                if (chord != null && shortcut != null && chord.isUsable) {
                    val earlier = bound.put(chord, shortcut)
                    if (earlier != null && earlier != shortcut) {
                        duplicates += ShortcutConflict.DuplicateInText(chord, shortcut, earlier)
                    }
                }
            }
        return ShortcutAnalysis(parsed.map, parsed.rejected, duplicates)
    }
}
