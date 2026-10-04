// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FontCatalogTest {
    private val fira = CustomFont("font-fira-1", "Fira Code", "font-fira-1.ttf")
    private val hack = CustomFont("font-hack-2", "Hack", "font-hack-2.ttf")

    @Test
    fun anImportedFontIsFoundByIdAndTheBundledOneIsNot() {
        assertEquals(fira, FontCatalog.resolve("font-fira-1", listOf(fira, hack)))
        assertNull(FontCatalog.resolve(FontCatalog.BUNDLED_ID, listOf(fira)))
        assertNull(FontCatalog.resolve("gone", listOf(fira)))
    }

    @Test
    fun aFontThatIsGoneFallsBackToTheBundledOne() {
        assertEquals("font-fira-1", FontCatalog.idOrBundled("font-fira-1", listOf(fira)))
        assertEquals(FontCatalog.BUNDLED_ID, FontCatalog.idOrBundled("font-fira-1", emptyList()))
        assertEquals(
            FontCatalog.BUNDLED_ID,
            FontCatalog.idOrBundled(FontCatalog.BUNDLED_ID, listOf(fira))
        )
    }

    @Test
    fun aFontIsAddedAndRemoved() {
        val added = (FontCatalog.add(listOf(fira), hack) as Outcome.Success).value

        assertEquals(listOf(fira, hack), added)
        assertEquals(listOf(hack), FontCatalog.remove(added, "font-fira-1"))
    }

    @Test
    fun namesAreUniqueIgnoringCaseAndMayNotTakeTheBundledName() {
        val sameName = hack.copy(id = "font-other", name = "FIRA code")
        val bundled = hack.copy(id = "font-b", name = "jetbrains mono")

        assertEquals(
            DomainError.NameTaken("FIRA code"),
            (FontCatalog.add(listOf(fira), sameName) as Outcome.Failure).error
        )
        assertTrue(FontCatalog.add(emptyList(), bundled) is Outcome.Failure)
    }

    @Test
    fun anIdCannotBeUsedTwice() {
        val sameId = hack.copy(id = fira.id)

        assertTrue(FontCatalog.add(listOf(fira), sameId) is Outcome.Failure)
    }

    @Test
    fun thereIsALimitOfImportedFonts() {
        val many = (1..FontCatalog.MAX_CUSTOM_FONTS).map {
            CustomFont("font-$it", "Font $it", "font-$it.ttf")
        }

        assertEquals(
            DomainError.InvalidValue("customFonts"),
            (FontCatalog.add(many, hack) as Outcome.Failure).error
        )
    }

    @Test
    fun theNameComesFromTheFileName() {
        assertEquals("Fira Code Regular", FontCatalog.nameFromFile("Fira_Code_Regular.ttf"))
        assertEquals("Hack", FontCatalog.nameFromFile("download/Hack.otf"))
        assertEquals("Font", FontCatalog.nameFromFile(".ttf"))
        assertEquals("AB", FontCatalog.nameFromFile("A\u0000B.ttf"))
        assertEquals(
            FontCatalog.MAX_NAME_LENGTH,
            FontCatalog.nameFromFile("x".repeat(100) + ".ttf").length
        )
    }

    @Test
    fun theIdIsASafeSlug() {
        assertEquals("font-fira-code", FontCatalog.idFor("Fira Code!"))
        assertEquals("font-custom", FontCatalog.idFor("!!!"))
    }

    @Test
    fun theListSurvivesStorage() {
        assertEquals(
            listOf(fira, hack),
            FontCatalog.decodeList(FontCatalog.encodeList(listOf(fira, hack)))
        )
    }

    @Test
    fun aDamagedListGivesNoFonts() {
        assertEquals(emptyList<CustomFont>(), FontCatalog.decodeList("not json"))
        assertEquals(emptyList<CustomFont>(), FontCatalog.decodeList("{\"a\":1}"))
    }

    @Test
    fun anEntryWithAPathAsFileNameIsDropped() {
        val evil = FontCatalog.encodeList(
            listOf(
                fira,
                CustomFont("x", "Evil", "../../databases/app.db"),
                CustomFont("y", "Evil2", "a\\b.ttf"),
                CustomFont("z", "Dots", ".."),
                CustomFont("w", "Dot", "."),
                CustomFont("", "NoId", "a.ttf")
            )
        )

        assertEquals(listOf(fira), FontCatalog.decodeList(evil))
    }
}
