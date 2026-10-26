// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PassphrasesTest {
    @Test
    fun noPassphraseAtAllIsFine() {
        assertEquals(Passphrases.Check.OK, Passphrases.check("", ""))
    }

    @Test
    fun aFourDigitPinIsTheShortestAccepted() {
        assertEquals(Passphrases.Check.OK, Passphrases.check("1234", "1234"))
        assertEquals(Passphrases.Check.TOO_SHORT, Passphrases.check("123", "123"))
    }

    @Test
    fun aConfirmationThatDiffersOrIsMissingIsAMismatch() {
        assertEquals(Passphrases.Check.MISMATCH, Passphrases.check("12345", "12346"))
        assertEquals(Passphrases.Check.MISMATCH, Passphrases.check("12345", ""))
        assertEquals(Passphrases.Check.TOO_SHORT, Passphrases.check("", "12345"))
    }

    @Test
    fun bytesAreUtf8AndTheSourceIsUntouched() {
        val chars = "añ€".toCharArray()
        assertArrayEquals("añ€".toByteArray(Charsets.UTF_8), Passphrases.toBytes(chars))
        assertEquals("añ€", String(chars))
    }
}
