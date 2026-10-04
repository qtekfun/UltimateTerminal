// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SchemeCatalogTest {
    private fun custom(name: String) =
        BuiltInSchemes.nord.copy(id = SchemeCodec.idFor(name), name = name, builtIn = false)

    @Test
    fun aKnownIdResolvesToItsScheme() {
        assertSame(BuiltInSchemes.dracula, SchemeCatalog.resolve("dracula", emptyList()))
        val mine = custom("Mine")
        assertEquals(mine, SchemeCatalog.resolve("custom-mine", listOf(mine)))
    }

    @Test
    fun anUnknownIdFallsBackToTheDefault() {
        assertSame(BuiltInSchemes.dracula, SchemeCatalog.resolve("custom-gone", emptyList()))
        assertSame(BuiltInSchemes.dracula, SchemeCatalog.resolve("", emptyList()))
    }

    @Test
    fun anImportedSchemeIsAdded() {
        val mine = custom("Mine")
        assertEquals(listOf(mine), SchemeCatalog.add(emptyList(), mine).getOrNull())
    }

    @Test
    fun namesAreUniqueAmongAllSchemesIgnoringCase() {
        val mine = custom("Mine")
        assertEquals(
            Outcome.Failure(DomainError.NameTaken("NORD")),
            SchemeCatalog.add(emptyList(), custom("NORD"))
        )
        assertEquals(
            Outcome.Failure(DomainError.NameTaken("mine")),
            SchemeCatalog.add(listOf(mine), custom("mine"))
        )
    }

    @Test
    fun thereIsALimitOnImportedSchemes() {
        val full = (1..SchemeCatalog.MAX_CUSTOM_SCHEMES).map { custom("Scheme $it") }
        assertEquals(
            Outcome.Failure(DomainError.InvalidValue("customSchemes")),
            SchemeCatalog.add(full, custom("One more"))
        )
    }

    @Test
    fun aSchemeCanBeRemoved() {
        val a = custom("A")
        val b = custom("B")
        assertEquals(listOf(b), SchemeCatalog.remove(listOf(a, b), a.id))
        assertEquals(listOf(a, b), SchemeCatalog.remove(listOf(a, b), "custom-none"))
    }
}
