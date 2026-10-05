// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap

/** One line of the shortcuts list: what it does and the combinations that do it, ready to show. */
data class ShortcutRow(val shortcut: AppShortcut, val chords: List<String>)

/** How the shortcuts are listed in Settings: a fixed order, readable key names, tabs as one line. */
object ShortcutDisplay {
    private val order: List<AppShortcut> = listOf(
        AppShortcut.NewTab, AppShortcut.CloseTab, AppShortcut.NextTab, AppShortcut.PreviousTab,
        AppShortcut.SelectTab(1),
        AppShortcut.SplitHorizontal, AppShortcut.SplitVertical, AppShortcut.ClosePane,
        AppShortcut.ToggleZoom,
        AppShortcut.FocusLeft, AppShortcut.FocusRight, AppShortcut.FocusUp, AppShortcut.FocusDown,
        AppShortcut.ToggleBroadcast, AppShortcut.SaveLayout, AppShortcut.OpenLayouts,
        AppShortcut.Copy, AppShortcut.Paste,
        AppShortcut.ZoomIn, AppShortcut.ZoomOut, AppShortcut.ZoomReset
    )

    private val keyNames = mapOf(
        "tab" to "Tab", "insert" to "Insert", "equals" to "=", "minus" to "-", "plus" to "+",
        "numpad_add" to "Num +", "numpad_subtract" to "Num -", "page_up" to "Page Up",
        "page_down" to "Page Down", "left" to "←", "right" to "→", "up" to "↑", "down" to "↓"
    )

    /** The shortcuts of [map] that have a binding, in the fixed order, each with its chords sorted. */
    fun rows(map: ShortcutMap): List<ShortcutRow> = allRows(map).filter { it.chords.isNotEmpty() }

    /**
     * Every shortcut in the fixed order, also those with no combination left, so the user can give
     * one back to an action they emptied. The tab numbers are one row, which stands for the nine.
     */
    fun allRows(map: ShortcutMap): List<ShortcutRow> {
        val byShortcut = map.all.entries.groupBy({ it.value }, { it.key })
        return order.map { shortcut ->
            if (shortcut is AppShortcut.SelectTab) {
                tabRow(byShortcut) ?: ShortcutRow(shortcut, emptyList())
            } else {
                ShortcutRow(shortcut, byShortcut[shortcut].orEmpty().mapNotNull(::pretty).sorted())
            }
        }
    }

    /** Alt+1…9 as one line when the nine share their modifiers, otherwise each one. */
    private fun tabRow(byShortcut: Map<AppShortcut, List<KeyChord>>): ShortcutRow? {
        val chords = (1..AppShortcut.MAX_DIRECT_TAB).flatMap { n ->
            byShortcut[AppShortcut.SelectTab(n)].orEmpty()
        }
        if (chords.isEmpty()) return null
        val sameModifiers = chords.map { Triple(it.ctrl, it.alt, it.shift) }.distinct().size == 1
        val text = if (sameModifiers && chords.size == AppShortcut.MAX_DIRECT_TAB) {
            val prefix = pretty(chords.first())?.substringBeforeLast('+', "")?.let {
                "$it+"
            }.orEmpty()
            "${prefix}1–${AppShortcut.MAX_DIRECT_TAB}"
        } else {
            null
        }
        return ShortcutRow(
            AppShortcut.SelectTab(1),
            text?.let(::listOf) ?: chords.mapNotNull(::pretty)
        )
    }

    /** `ctrl+shift+t` as "Ctrl+Shift+T"; null for a key that has no name. */
    fun pretty(chord: KeyChord): String? {
        val parts = chord.format()?.split('+') ?: return null
        val key = parts.last()
        val shown = keyNames[key] ?: key.replaceFirstChar { it.uppercase() }
        return (
            parts.dropLast(1).map {
                it.replaceFirstChar { c -> c.uppercase() }
            } + shown
            ).joinToString("+")
    }
}
