// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

/**
 * A named set of terminal settings (SPEC RF-12). A tab or pane uses one profile. [id] is 0 for a
 * profile that has not been saved yet.
 */
data class Profile(
    val id: Long = 0L,
    val name: String,
    val colorSchemeId: String = DEFAULT_COLOR_SCHEME,
    val fontFamily: String = DEFAULT_FONT,
    val fontSizeSp: Int = DEFAULT_FONT_SIZE,
    val scrollbackLines: Int = DEFAULT_SCROLLBACK,
    /** The distro to open; null means the default distro. */
    val distroId: Long? = null,
    val user: String? = null,
    val startupCommand: String? = null
) {
    companion object {
        const val DEFAULT_COLOR_SCHEME = "default"
        const val DEFAULT_FONT = "monospace"
        const val DEFAULT_FONT_SIZE = 14
        const val DEFAULT_SCROLLBACK = 10_000
        val FONT_SIZE_RANGE = 6..72
        val SCROLLBACK_RANGE = 100..1_000_000
    }
}
