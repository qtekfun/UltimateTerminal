// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

/**
 * What to run in a new session instead of Android's own shell (for example proot running `ssh`
 * inside a distro). [onClosed] runs once when the session ends or is closed, to remove anything the
 * launch put on disk, such as a key file.
 */
class SessionLaunch(
    val command: List<String>,
    val environment: Map<String, String>,
    val onClosed: () -> Unit = {}
) {
    // Not a data class: the command line can name a key file, so it is kept out of toString.
    override fun toString(): String = "SessionLaunch(${command.size} arguments)"
}
