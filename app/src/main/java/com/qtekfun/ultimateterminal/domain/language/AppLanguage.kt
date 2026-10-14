// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.language

/**
 * The language the app's interface uses: the system's, or one of those that ship translations. A new
 * language is a `values-xx/strings.xml` (the system's per-app language list is generated from the
 * resources) and one more entry here.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es");

    companion object {
        /** The language for a stored or system language [tag]; anything unknown is [SYSTEM]. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase().orEmpty()
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }
    }
}
