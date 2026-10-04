// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.appearance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ColorHexTest {
    @Test
    fun theUsualFormsAreParsedAsOpaqueColors() {
        assertEquals(0xFF112233.toInt(), ColorHex.parse("#112233"))
        assertEquals(0xFF112233.toInt(), ColorHex.parse("112233"))
        assertEquals(0xFF112233.toInt(), ColorHex.parse("  #123  "))
        assertEquals(0xFFAABBCC.toInt(), ColorHex.parse("#AaBbCc"))
    }

    @Test
    fun anythingElseIsRejected() {
        assertNull(ColorHex.parse(""))
        assertNull(ColorHex.parse("#"))
        assertNull(ColorHex.parse("#12345"))
        assertNull(ColorHex.parse("#1234567"))
        assertNull(ColorHex.parse("#gggggg"))
        assertNull(ColorHex.parse("red"))
    }

    @Test
    fun aColorIsWrittenInLowerCaseWithoutAlpha() {
        assertEquals("#abcdef", ColorHex.format(0xFFABCDEF.toInt()))
        assertEquals("#000000", ColorHex.format(0))
    }

    @Test
    fun whatIsWrittenCanBeReadBack() {
        val color = 0xFF3A5F9C.toInt()

        assertEquals(color, ColorHex.parse(ColorHex.format(color)))
    }
}
