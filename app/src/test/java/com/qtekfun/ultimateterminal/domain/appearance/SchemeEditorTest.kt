// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCatalog
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchemeEditorTest {
    private val base = BuiltInSchemes.dracula
    private val all = BuiltInSchemes.all

    private fun mine(name: String) = SchemeEditor.blank(name, base, all)

    @Test
    fun aDuplicateGetsAFreeNameAndIsNeverBuiltIn() {
        val copy = SchemeEditor.duplicate(base, "Copy of", all)

        assertEquals("Copy of Dracula", copy.name)
        assertEquals("custom-copy-of-dracula", copy.id)
        assertFalse(copy.builtIn)
        assertEquals(base.ansi, copy.ansi)
    }

    @Test
    fun aDuplicateOfADuplicateGetsANumber() {
        val first = SchemeEditor.duplicate(base, "Copy of", all)
        val second = SchemeEditor.duplicate(base, "Copy of", all + first)

        assertEquals("Copy of Dracula 2", second.name)
    }

    @Test
    fun aLongNameIsCutSoTheNumberStillFits() {
        val long = base.copy(name = "x".repeat(60))
        val first = SchemeEditor.blank("y".repeat(60), long, all)
        val second = SchemeEditor.blank("y".repeat(60), long, all + first)

        assertEquals(SchemeCodec.MAX_NAME_LENGTH, first.name.length)
        assertEquals(SchemeCodec.MAX_NAME_LENGTH, second.name.length)
        assertTrue(second.name.endsWith(" 2"))
    }

    @Test
    fun aBlankSchemeTakesTheNameOrTheNearestFreeOne() {
        assertEquals("Fresh", mine("Fresh").name)
        assertEquals("dracula 2", mine("dracula").name)
    }

    @Test
    fun eachSlotChangesOnlyItsOwnColor() {
        val red = 0xFFFF0000.toInt()

        assertEquals(red, SchemeEditor.withColor(base, ColorSlot.Foreground, red).foreground)
        assertEquals(red, SchemeEditor.withColor(base, ColorSlot.Background, red).background)
        assertEquals(red, SchemeEditor.withColor(base, ColorSlot.Cursor, red).cursor)
        assertEquals(red, SchemeEditor.withColor(base, ColorSlot.Selection, red).selection)
        val ansi = SchemeEditor.withColor(base, ColorSlot.Ansi(5), red)
        assertEquals(red, ansi.ansi[5])
        assertEquals(
            base.ansi.filterIndexed { i, _ -> i != 5 },
            ansi.ansi.filterIndexed { i, _ ->
                i !=
                    5
            }
        )
        assertEquals(base.foreground, ansi.foreground)
    }

    @Test
    fun aColorWithoutAlphaBecomesOpaque() {
        assertEquals(
            0xFF123456.toInt(),
            SchemeEditor.withColor(base, ColorSlot.Cursor, 0x123456).cursor
        )
    }

    @Test
    fun renamingFollowsTheNameAndRejectsBadNames() {
        val renamed = (SchemeEditor.renamed(mine("Old"), "  New Name ") as Outcome.Success).value

        assertEquals("New Name", renamed.name)
        assertEquals("custom-new-name", renamed.id)
        assertTrue(SchemeEditor.renamed(renamed, "   ") is Outcome.Failure)
        assertTrue(SchemeEditor.renamed(renamed, "a\u0000b") is Outcome.Failure)
        assertTrue(SchemeEditor.renamed(renamed, "x".repeat(41)) is Outcome.Failure)
    }

    @Test
    fun aNewSchemeIsAddedToTheCustomOnes() {
        val result = SchemeEditor.save(emptyList(), null, mine("Mine"))

        assertEquals(listOf("Mine"), (result as Outcome.Success).value.map { it.name })
    }

    @Test
    fun savingAnEditReplacesTheOldVersionInPlace() {
        val first = mine("One")
        val second = mine("Two")
        val edited = SchemeEditor.withColor(first, ColorSlot.Cursor, 0xFF00FF00.toInt())

        val result = SchemeEditor.save(listOf(first, second), first.id, edited)

        assertEquals(listOf(edited, second), (result as Outcome.Success).value)
    }

    @Test
    fun anEditThatRenamesTheSchemeKeepsOneCopyOnly() {
        val first = mine("One")
        val renamed = (SchemeEditor.renamed(first, "Uno") as Outcome.Success).value

        val result = SchemeEditor.save(listOf(first), first.id, renamed)

        assertEquals(listOf("Uno"), (result as Outcome.Success).value.map { it.name })
    }

    @Test
    fun aNameOfAnotherSchemeOrABuiltInOneIsRefused() {
        val first = mine("One")
        val second = mine("Two")

        val againstCustom = SchemeEditor.save(
            listOf(first, second),
            second.id,
            second.copy(name = "ONE")
        )
        val againstBuiltIn = SchemeEditor.save(emptyList(), null, base.copy(builtIn = false))

        assertTrue(againstCustom is Outcome.Failure)
        assertEquals(DomainError.NameTaken("Dracula"), (againstBuiltIn as Outcome.Failure).error)
    }

    @Test
    fun theLimitOfCustomSchemesApplies() {
        val many = (1..SchemeCatalog.MAX_CUSTOM_SCHEMES).map { mine("S$it") }

        val result = SchemeEditor.save(many, null, mine("One more"))

        assertEquals(DomainError.InvalidValue("customSchemes"), (result as Outcome.Failure).error)
    }

    @Test
    fun savingAnIdThatIsGoneAddsTheScheme() {
        val result = SchemeEditor.save(listOf(mine("One")), "custom-gone", mine("Two"))

        assertEquals(2, (result as Outcome.Success).value.size)
    }

    @Test
    fun theBuiltInSchemesHaveNoContrastProblemsInTheTextOrTheCursor() {
        BuiltInSchemes.all.forEach { scheme ->
            val textIssues = SchemeEditor.contrastIssues(scheme).filter {
                it.slot == ColorSlot.Foreground || it.slot == ColorSlot.Cursor
            }
            assertTrue(textIssues.isEmpty(), scheme.name)
        }
    }

    @Test
    fun aSchemeWithGreyTextOnGreyIsFlaggedWorstFirst() {
        val bad = base.copy(foreground = 0xFF777777.toInt(), background = 0xFF808080.toInt())

        val issues = SchemeEditor.contrastIssues(bad)

        assertEquals(ColorSlot.Foreground, issues.first().slot)
        assertTrue(issues.zipWithNext().all { (a, b) -> a.ratio <= b.ratio })
        assertTrue(issues.first().ratio < issues.first().minimum)
    }

    @Test
    fun blackAndBrightBlackAreAllowedToBlendIntoTheBackground() {
        val quiet = base.copy(
            ansi = base.ansi.mapIndexed { i, c -> if (i == 0 || i == 8) base.background else c }
        )

        assertTrue(
            SchemeEditor.contrastIssues(quiet).none {
                (it.slot as? ColorSlot.Ansi)?.index in setOf(0, 8)
            }
        )
    }
}
