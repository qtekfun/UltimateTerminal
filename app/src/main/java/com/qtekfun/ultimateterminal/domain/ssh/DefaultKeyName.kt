// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.model.Validation

/** The name suggested for a key imported from a file: the file's own name, made unique. */
object DefaultKeyName {
    private val keyExtensions = listOf(".pem", ".key", ".txt")

    /**
     * Suggests a name from [displayName], the picked file's name. Trims it and drops a trailing
     * `.pem`, `.key` or `.txt`; cuts it to the name-length limit; and appends " 2", " 3"... when
     * [existingNames] already has it (case-insensitively, as saving does). Returns "" (no
     * suggestion) when there is no usable name, so the user types one.
     */
    fun from(displayName: String?, existingNames: Collection<String>): String {
        val trimmed = displayName.orEmpty().trim()
        val extension = keyExtensions.firstOrNull {
            trimmed.length > it.length && trimmed.endsWith(it, ignoreCase = true)
        }
        val base = trimmed.dropLast(extension?.length ?: 0).trim().filterNot { it.isISOControl() }
        val taken = existingNames.map { it.trim().lowercase() }.toSet()
        val max = Validation.MAX_NAME_LENGTH
        return if (base.isEmpty() || base == ".") {
            ""
        } else {
            var n = 1
            var candidate = base.take(max).trimEnd()
            while (candidate.lowercase() in taken) {
                n++
                val suffix = " $n"
                candidate = base.take(max - suffix.length).trimEnd() + suffix
            }
            candidate
        }
    }
}
