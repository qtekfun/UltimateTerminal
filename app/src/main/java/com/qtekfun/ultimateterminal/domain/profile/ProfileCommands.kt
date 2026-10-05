// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

/** What the keyboard shortcuts of T12b ask for: the broadcast and the saved layouts. */
interface ProfileCommands {
    /** Turns on or off the typing in every pane of the active tab. */
    fun toggleBroadcast()

    /** Asks for the name to save the panes of the active tab under. */
    fun saveLayout()

    /** Opens the list of saved layouts. */
    fun openLayouts()
}
