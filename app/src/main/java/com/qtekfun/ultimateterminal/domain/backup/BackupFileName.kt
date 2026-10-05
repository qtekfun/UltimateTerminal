// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.backup

import java.time.LocalDate

/** The name suggested to the system file picker for a new backup. */
object BackupFileName {
    const val EXTENSION = "utbackup"
    private const val PREFIX = "UltimateTerminal"
    private const val MAX_LABEL = 40

    /**
     * `UltimateTerminal-<date>.utbackup` for everything, `UltimateTerminal-settings-<date>` for the
     * settings and `UltimateTerminal-<distro>-<date>` for a distro (its name reduced to letters,
     * digits and underscores, joined by dashes). The picker lets the user change it.
     */
    fun suggest(kind: BackupKind, distroName: String?, date: LocalDate): String {
        val label = when (kind) {
            BackupKind.CONFIG -> "-settings"
            BackupKind.ALL -> ""
            BackupKind.DISTRO -> "-" + safe(distroName.orEmpty()).ifEmpty { "distro" }
        }
        return "$PREFIX$label-$date.$EXTENSION"
    }

    private fun safe(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_]+"), "-").trim('-').take(MAX_LABEL).trim('-')
}
