// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.language

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLanguageTest {
    @Test
    fun knownTagsMapToTheirLanguage() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
    }

    @Test
    fun regionAndCaseAreIgnored() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es-MX"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("ES_es"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
    }

    @Test
    fun unknownEmptyOrMissingFallBackToTheSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
    }

    @Test
    fun everyLanguageHasADistinctTag() {
        assertEquals(AppLanguage.entries.size, AppLanguage.entries.map { it.tag }.toSet().size)
    }
}
