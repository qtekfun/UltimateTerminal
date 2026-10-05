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
     * `UltimateTerminal-<what>-<date>.utbackup`, where `<what>` is the distro's name for a distro
     * backup (reduced to letters, digits and underscores, joined by dashes) and `settings` or `all`
     * otherwise. The picker lets the user change it.
     */
    fun suggest(kind: BackupKind, distroName: String?, date: LocalDate): String {
        val label = when (kind) {
            BackupKind.CONFIG -> "settings"
            BackupKind.ALL -> "all"
            BackupKind.DISTRO -> safe(distroName.orEmpty()).ifEmpty { "distro" }
        }
        return "$PREFIX-$label-$date.$EXTENSION"
    }

    private fun safe(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_]+"), "-").trim('-').take(MAX_LABEL).trim('-')
}
