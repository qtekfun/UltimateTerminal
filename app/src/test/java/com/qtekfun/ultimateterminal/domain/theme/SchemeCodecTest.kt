// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchemeCodecTest {
    private val sample = BuiltInSchemes.dracula

    private fun json(
        version: Int = 1,
        name: String = "Mine",
        ansi: List<String> = List(16) { "#0000%02x".format(it) },
        foreground: String = "#ffffff",
        background: String = "#000000",
        cursor: String = "#ffffff",
        selection: String = "#333333"
    ) = """{"version":$version,"name":"$name","ansi":[${ansi.joinToString(",") { "\"$it\"" }}],""" +
        """"foreground":"$foreground","background":"$background","cursor":"$cursor",""" +
        """"selection":"$selection"}"""

    private fun failure(text: String) = (SchemeCodec.decode(text) as Outcome.Failure).error

    @Test
    fun aSchemeSurvivesAnExportAndAnImport() {
        val imported = SchemeCodec.decode(SchemeCodec.encode(sample)).getOrNull()!!

        assertEquals(sample.name, imported.name)
        assertEquals(sample.ansi, imported.ansi)
        assertEquals(sample.foreground, imported.foreground)
        assertEquals(sample.background, imported.background)
        assertEquals(sample.cursor, imported.cursor)
        assertEquals(sample.selection, imported.selection)
        assertEquals("custom-dracula", imported.id)
        assertEquals(false, imported.builtIn)
    }

    @Test
    fun theFileCarriesItsVersionAndReadableColors() {
        val text = SchemeCodec.encode(sample)
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"#282a36\""))
    }

    @Test
    fun aHandWrittenFileIsAccepted() {
        val imported = SchemeCodec.decode(
            json(name = "  Mine  ", foreground = "#FFAA00")
        ).getOrNull()!!
        assertEquals("Mine", imported.name)
        assertEquals(0xFFFFAA00.toInt(), imported.foreground)
    }

    @Test
    fun unknownFieldsAreIgnoredSoNewerFilesStillOpen() {
        val text = json().dropLast(1) + ""","author":"someone"}"""
        assertTrue(SchemeCodec.decode(text) is Outcome.Success)
    }

    @Test
    fun otherVersionsAreRefused() {
        assertEquals(DomainError.InvalidValue("version"), failure(json(version = 2)))
        assertEquals(DomainError.InvalidValue("version"), failure(json(version = 0)))
    }

    @Test
    fun theNameIsChecked() {
        assertTrue(failure(json(name = "")) is DomainError.InvalidName)
        assertTrue(failure(json(name = "   ")) is DomainError.InvalidName)
        assertTrue(
            failure(
                json(name = "x".repeat(SchemeCodec.MAX_NAME_LENGTH + 1))
            ) is DomainError.InvalidName
        )
        assertTrue(failure(json(name = "a\\u0007b")) is DomainError.InvalidName)
        assertTrue(
            SchemeCodec.decode(
                json(name = "x".repeat(SchemeCodec.MAX_NAME_LENGTH))
            ) is Outcome.Success
        )
    }

    @Test
    fun exactlySixteenAnsiColorsAreNeeded() {
        assertEquals(DomainError.InvalidValue("ansi"), failure(json(ansi = List(15) { "#000000" })))
        assertEquals(DomainError.InvalidValue("ansi"), failure(json(ansi = List(17) { "#000000" })))
    }

    @Test
    fun colorsMustBeSixDigitHex() {
        val bad = listOf("red", "#fff", "#12345", "#1234567", "#gggggg", "000000", "#00000g", "")
        bad.forEach { color ->
            val expected = DomainError.InvalidValue("color")
            assertEquals(expected, failure(json(foreground = color)), color)
            assertEquals(expected, failure(json(background = color)), color)
            assertEquals(expected, failure(json(cursor = color)), color)
            assertEquals(expected, failure(json(selection = color)), color)
            assertEquals(expected, failure(json(ansi = List(15) { "#000000" } + color)), color)
        }
    }

    @Test
    fun textThatIsNotASchemeIsRefusedWithoutThrowing() {
        listOf("", "not json", "[]", "{}", """{"version":1}""", "null", "42").forEach { text ->
            assertEquals(DomainError.InvalidValue("scheme"), failure(text), text)
        }
    }

    @Test
    fun theIdIsDerivedFromTheNameAndNeverCollidesWithABuiltInOne() {
        assertEquals("custom-my-scheme-2", SchemeCodec.idFor("  My Scheme #2!  "))
        assertEquals("custom-nord", SchemeCodec.idFor("Nord"))
        assertTrue(BuiltInSchemes.all.none { SchemeCodec.idFor(it.name) == it.id })
    }

    @Test
    fun theStoredListRoundTripsInOrder() {
        val first = SchemeCodec.decode(SchemeCodec.encode(BuiltInSchemes.nord)).getOrNull()!!
        val second = SchemeCodec.decode(
            SchemeCodec.encode(BuiltInSchemes.gruvboxDark)
        ).getOrNull()!!

        assertEquals(
            listOf(first, second),
            SchemeCodec.decodeList(SchemeCodec.encodeList(listOf(first, second)))
        )
        assertEquals(
            emptyList<TerminalColorScheme>(),
            SchemeCodec.decodeList(SchemeCodec.encodeList(emptyList()))
        )
    }

    @Test
    fun aDamagedStoredListIsEmptyAndAnInvalidEntryIsDropped() {
        assertEquals(emptyList<TerminalColorScheme>(), SchemeCodec.decodeList("not json"))
        assertEquals(emptyList<TerminalColorScheme>(), SchemeCodec.decodeList(""))
        val good = SchemeCodec.decode(SchemeCodec.encode(BuiltInSchemes.nord)).getOrNull()!!
        val stored =
            "[" + json(name = "") + "," + json(version = 9) + "," + SchemeCodec.encode(good) + "]"
        assertEquals(listOf(good), SchemeCodec.decodeList(stored))
    }
}
