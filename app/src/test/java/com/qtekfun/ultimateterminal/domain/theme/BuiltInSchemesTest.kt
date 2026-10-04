// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInSchemesTest {
    private val all = BuiltInSchemes.all

    private fun check(condition: Boolean, scheme: TerminalColorScheme, what: String) =
        assertTrue(condition, "${scheme.name}: $what")

    @Test
    fun theCatalogHasTheRequestedSchemes() {
        val ids = all.map { it.id }
        assertEquals(
            listOf(
                "tango",
                "solarized-dark",
                "solarized-light",
                "dracula",
                "gruvbox-dark",
                "nord",
                "oled"
            ),
            ids
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertTrue(all.all { it.builtIn })
        assertTrue(all.any { it.id == BuiltInSchemes.DEFAULT_ID })
    }

    @Test
    fun everyColorIsOpaque() {
        all.forEach { scheme ->
            val colors = scheme.ansi + listOf(
                scheme.foreground,
                scheme.background,
                scheme.cursor,
                scheme.selection
            )
            check(colors.all { it ushr 24 == 0xFF }, scheme, "has a transparent color")
        }
    }

    @Test
    fun defaultTextIsReadable() {
        all.forEach { scheme ->
            val ratio = ColorMath.contrast(scheme.foreground, scheme.background)
            check(ratio >= 4.5, scheme, "text on background is $ratio:1, below 4.5:1 (WCAG AA)")
        }
    }

    @Test
    fun theCursorAndTheSelectedTextAreReadable() {
        all.forEach { scheme ->
            val cursor = ColorMath.contrast(scheme.cursor, scheme.background)
            check(cursor >= 3.0, scheme, "cursor on background is $cursor:1")
            val selected = ColorMath.contrast(scheme.foreground, scheme.selection)
            check(selected >= 3.0, scheme, "text on selection is $selected:1")
        }
    }

    @Test
    fun everyColorAProgramMayPrintIsReadableOnTheBackground() {
        // Red, green, yellow, blue, magenta and cyan, normal and bright: what ls, git, grep and
        // diff colorize with. Indexes 0, 7, 8 and 15 are the blacks and whites, tested below.
        val printable = (1..6) + (9..14)
        all.forEach { scheme ->
            printable.forEach { index ->
                val ratio = ColorMath.contrast(scheme.ansi[index], scheme.background)
                check(ratio >= 3.0, scheme, "ANSI color $index is $ratio:1 on the background")
            }
        }
    }

    @Test
    fun brightBlackIsVisibleAsGreyOnTheBackground() {
        // Shells use it for suggestions and comments; it must not disappear into the background.
        all.forEach { scheme ->
            val ratio = ColorMath.contrast(scheme.ansi[8], scheme.background)
            check(ratio >= 1.5, scheme, "bright black is $ratio:1 on the background")
        }
    }

    @Test
    fun theDarkAndLightSchemesAreWhatTheirNamesSay() {
        assertFalse(BuiltInSchemes.solarizedLight.isDark)
        assertTrue(BuiltInSchemes.solarizedDark.isDark)
        assertTrue(BuiltInSchemes.oled.isDark)
        assertEquals(TerminalColorScheme.BLACK, BuiltInSchemes.oled.background)
    }

    @Test
    fun everyDarkSchemeStaysReadableOnPureBlack() {
        all.filter { it.isDark }.forEach { dark ->
            val oled = dark.forOled()
            assertEquals(TerminalColorScheme.BLACK, oled.background)
            val text = ColorMath.contrast(oled.foreground, oled.background)
            check(text >= 4.5, dark, "text on black is $text:1")
            (1..6).plus(9..14).forEach { index ->
                val ratio = ColorMath.contrast(oled.ansi[index], oled.background)
                check(ratio >= 3.0, dark, "ANSI color $index is $ratio:1 on black")
            }
        }
    }
}
