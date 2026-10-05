// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/** Whether the side tab bar gets out of the way of the terminal (SPEC RF-16). */
enum class SidebarMode {
    /** It shrinks to a rail when the terminal is used and opens again on request. */
    AUTO_COLLAPSE,

    /** It always shows the tabs in full. */
    ALWAYS_EXPANDED;

    companion object {
        val DEFAULT = AUTO_COLLAPSE

        /** The mode with that stored name; anything else (a missing or a later value) is [DEFAULT]. */
        fun parse(name: String?): SidebarMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** What can change the state of the sidebar. */
enum class SidebarEvent {
    /** A tap or a focus inside a pane: the user is working in the terminal. */
    TERMINAL_USED,

    /** A tap on the rail, on its chevron, or a screen reader's action. */
    EXPAND,

    /** The chevron of the open sidebar, or a screen reader's action. */
    COLLAPSE,

    /** Something that needs the tab actions (a long press on a tab of the rail). */
    TAB_ACTIONS_NEEDED
}

/**
 * The state machine of the side tab bar: whether it is open ([expanded]) or a slim rail. Pure, so
 * the rules are tested without a screen. In [SidebarMode.ALWAYS_EXPANDED] it is always open and
 * nothing collapses it.
 */
object SidebarState {
    /** Whether the sidebar is open after [event], whatever it was before: every event decides. */
    fun expandedAfter(event: SidebarEvent, mode: SidebarMode): Boolean = when {
        mode == SidebarMode.ALWAYS_EXPANDED -> true
        event == SidebarEvent.TERMINAL_USED || event == SidebarEvent.COLLAPSE -> false
        else -> true
    }

    /** What the state becomes when the mode changes: the always-open mode opens it. */
    fun onMode(expanded: Boolean, mode: SidebarMode): Boolean =
        mode == SidebarMode.ALWAYS_EXPANDED || expanded
}

/** Widths of the side tab bar, in dp; the rail is as wide as a touch target plus its padding. */
object SidebarWidths {
    const val EXPANDED_DP = 192
    const val RAIL_DP = 56

    fun targetDp(expanded: Boolean): Int = if (expanded) EXPANDED_DP else RAIL_DP
}

/**
 * How far the terminal's area is drawn from its final place while the sidebar is [currentBarPx]
 * wide on its way to [targetBarPx]. The area is laid out once, at its final place and size (the
 * window minus the target width), so the ptys are told one size per change and not one per frame of
 * the animation; only the drawing slides, by this offset, which is zero when the sidebar rests
 * (D-T26-3).
 */
fun slideOffsetPx(currentBarPx: Float, targetBarPx: Int): Float = currentBarPx - targetBarPx

/** The mark of a tab in the rail: its first letter or digit in capitals, `?` for a blank name. */
fun tabInitial(name: String): String {
    val first = name.firstOrNull { it.isLetterOrDigit() } ?: return "?"
    return first.uppercaseChar().toString()
}
