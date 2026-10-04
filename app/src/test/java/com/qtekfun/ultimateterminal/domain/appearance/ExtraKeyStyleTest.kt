// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import com.qtekfun.ultimateterminal.domain.terminal.LatchState
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.ColorMath
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtraKeyStyleTest {
    /** Every built-in scheme, and each one in its pure-black OLED form. */
    private val schemes: List<TerminalColorScheme> =
        BuiltInSchemes.all.flatMap { listOf(it, it.forOled()) }

    private fun inputs(scheme: TerminalColorScheme): KeyChromeInputs {
        val chrome = ChromeColorsFor.scheme(scheme)
        return KeyChromeInputs(
            background = scheme.background,
            foreground = scheme.foreground,
            surface = chrome.surface,
            onSurface = chrome.onSurface,
            accent = chrome.accent,
            onAccent = chrome.onAccent
        )
    }

    private fun palette(scheme: TerminalColorScheme, style: ExtraKeyStyle) =
        ExtraKeyPaletteFor.of(inputs(scheme), style)

    private fun contrast(first: Int, second: Int) = ColorMath.contrast(first, second)

    /** Whether each channel of [color] lies between the same channel of [a] and of [b]. */
    private fun isBetween(color: Int, a: Int, b: Int): Boolean = listOf(16, 8, 0).all { shift ->
        val c = (color shr shift) and 0xFF
        val x = (a shr shift) and 0xFF
        val y = (b shr shift) and 0xFF
        c in minOf(x, y)..maxOf(x, y)
    }

    @Test
    fun theStyleIsFlatByDefaultAndAnythingUnknownIsFlat() {
        assertEquals(ExtraKeyStyle.FLAT, ExtraKeyStyle.DEFAULT)
        assertEquals(ExtraKeyStyle.FLAT, ExtraKeyStyle.parse(null))
        assertEquals(ExtraKeyStyle.FLAT, ExtraKeyStyle.parse("NEON"))
        assertEquals(ExtraKeyStyle.FLAT, ExtraKeyStyle.parse(""))
        ExtraKeyStyle.entries.forEach { assertEquals(it, ExtraKeyStyle.parse(it.name)) }
    }

    @Test
    fun everyLabelIsReadableOnWhatItIsDrawnOnInEverySchemeAndStyle() {
        for (scheme in schemes) {
            for (style in ExtraKeyStyle.entries) {
                val p = palette(scheme, style)
                val where = "${scheme.name} / $style"
                val rest = if (p.fill != 0) p.fill else p.tray
                assertTrue(contrast(p.label, rest) >= ExtraKeyPaletteFor.TEXT_MINIMUM, where)
                assertTrue(contrast(p.label, p.pressed) >= ExtraKeyPaletteFor.TEXT_MINIMUM, where)
            }
        }
    }

    @Test
    fun theLabelOfAnArmedAndOfALockedKeyIsReadableToo() {
        for (scheme in schemes) {
            for (style in ExtraKeyStyle.entries) {
                val p = palette(scheme, style)
                val where = "${scheme.name} / $style"
                assertTrue(contrast(p.onArmed, p.armed) >= ExtraKeyPaletteFor.TEXT_MINIMUM, where)
                assertTrue(contrast(p.onLocked, p.locked) >= ExtraKeyPaletteFor.TEXT_MINIMUM, where)
            }
        }
    }

    @Test
    fun theFlatStyleDrawsNoCapNoEdgeAndHasHairlines() {
        val flat = ExtraKeyStyle.FLAT
        for (scheme in schemes) {
            val p = palette(scheme, flat)
            assertEquals(0, p.fill, scheme.name)
            assertEquals(0, p.edge, scheme.name)
            assertNotEquals(0, p.separator, scheme.name)
            assertNotEquals(p.tray, p.pressed, scheme.name)
        }
        assertTrue(flat.separators && !flat.filledCaps && flat.pill && !flat.edge)
    }

    @Test
    fun theCapsuleStyleHasASoftCapNoEdgeAndNoHairlines() {
        val capsule = ExtraKeyStyle.CAPSULE
        for (scheme in schemes) {
            val p = palette(scheme, capsule)
            assertNotEquals(0, p.fill, scheme.name)
            assertNotEquals(p.tray, p.fill, scheme.name)
            assertEquals(0, p.edge, scheme.name)
            assertEquals(0, p.separator, scheme.name)
        }
        assertTrue(!capsule.separators && capsule.filledCaps && capsule.pill && !capsule.edge)
    }

    @Test
    fun theClassicStyleHasACapWithAnEdgeTakenFromTheSchemeNotFromAGrayOfTheSystem() {
        val classic = ExtraKeyStyle.CLASSIC
        for (scheme in schemes) {
            val p = palette(scheme, classic)
            assertNotEquals(0, p.fill, scheme.name)
            assertNotEquals(0, p.edge, scheme.name)
            assertEquals(0, p.separator, scheme.name)
            // The cap is the scheme's background pulled towards its foreground: every channel of
            // it lies between the two, never a color outside the segment (a system gray).
            assertTrue(isBetween(p.fill, scheme.background, scheme.foreground), scheme.name)
            assertNotEquals(scheme.background, p.fill, scheme.name)
        }
        assertTrue(!classic.separators && classic.filledCaps && !classic.pill && classic.edge)
    }

    @Test
    fun theFlatTrayIsNearTheTerminalBackgroundSoTheBarReadsAsPartOfIt() {
        for (scheme in schemes) {
            val flat = palette(scheme, ExtraKeyStyle.FLAT).tray
            val capsule = palette(scheme, ExtraKeyStyle.CAPSULE).tray
            assertTrue(
                kotlin.math.abs(
                    ColorMath.luminance(flat) - ColorMath.luminance(scheme.background)
                ) <=
                    kotlin.math.abs(
                        ColorMath.luminance(capsule) - ColorMath.luminance(scheme.background)
                    ),
                scheme.name
            )
        }
    }

    @Test
    fun theLookFollowsTheStateOfTheKey() {
        val scheme = BuiltInSchemes.dracula
        val flat = palette(scheme, ExtraKeyStyle.FLAT)

        assertEquals(KeyLook(0, flat.label, false), flat.look(LatchState.OFF, pressed = false))
        assertEquals(
            KeyLook(flat.pressed, flat.label, false),
            flat.look(LatchState.OFF, pressed = true)
        )
        assertEquals(KeyLook(flat.armed, flat.onArmed, false), flat.look(LatchState.ARMED, false))
        assertEquals(KeyLook(flat.locked, flat.onLocked, true), flat.look(LatchState.LOCKED, false))
    }

    @Test
    fun onlyAFlatLockedKeyCarriesTheUnderlineBecauseTheOthersHaveACapToShowIt() {
        val scheme = BuiltInSchemes.nord
        assertTrue(palette(scheme, ExtraKeyStyle.FLAT).look(LatchState.LOCKED, false).indicator)
        assertFalse(palette(scheme, ExtraKeyStyle.CAPSULE).look(LatchState.LOCKED, false).indicator)
        assertFalse(palette(scheme, ExtraKeyStyle.CLASSIC).look(LatchState.LOCKED, false).indicator)
    }

    @Test
    fun aKeyAtRestHasACapOnlyInTheStylesThatHaveOne() {
        val scheme = BuiltInSchemes.solarizedLight
        ExtraKeyStyle.entries.forEach { style ->
            val look = palette(scheme, style).look(LatchState.OFF, pressed = false)
            assertEquals(style.filledCaps, look.cap != 0, style.name)
        }
    }

    @Test
    fun theGeometryOfEachStyleIsFixedAndTheCapsuleKeepsAirBetweenKeys() {
        assertEquals(4, ExtraKeyStyle.FLAT.insetDp)
        assertEquals(3, ExtraKeyStyle.CAPSULE.insetDp)
        assertEquals(3, ExtraKeyStyle.CLASSIC.insetDp)
        // Two neighbouring caps are twice the inset apart: about 6 dp for the capsules.
        assertEquals(6, 2 * ExtraKeyStyle.CAPSULE.insetDp)
    }
}
