// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BcryptPbkdfTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun derive(password: String, salt: String, rounds: Int, length: Int) = hex(
        BcryptPbkdf.derive(password.toByteArray(), salt.toByteArray(), rounds, length)
    )

    @Test
    fun matchesTheReferenceVectorOfOpenBsd() {
        // The vector libsodium, Go's x/crypto and OpenBSD's regress test use.
        assertEquals(
            "5bbf0cc293587f1c3635555c27796598d47e579071bf427e9d8fbe842aba34d9",
            derive("password", "salt", 4, 32)
        )
    }

    @Test
    fun theTablesAreTheDigitsOfPi() {
        assertEquals(0x243f6a88, BlowfishTables.P.first())
        assertEquals(18, BlowfishTables.P.size)
        assertEquals(0xd1310ba6.toInt(), BlowfishTables.S0.first())
        listOf(BlowfishTables.S0, BlowfishTables.S1, BlowfishTables.S2, BlowfishTables.S3)
            .forEach { assertEquals(256, it.size) }
        assertEquals(0x3ac372e6, BlowfishTables.S3.last())
    }

    @Test
    fun anyLengthIsFilledAndAPrefixIsNotAShorterRun() {
        val long = derive("password", "salt", 2, 48)
        assertEquals(96, long.length)
        // The output is interleaved from two blocks, so a shorter request is a different key.
        assertFalse(long.startsWith(derive("password", "salt", 2, 32)))
        assertEquals(long, derive("password", "salt", 2, 48))
    }

    @Test
    fun passwordSaltAndRoundsAllChangeTheResult() {
        val base = derive("password", "salt", 2, 32)
        assertFalse(base == derive("passwore", "salt", 2, 32))
        assertFalse(base == derive("password", "salu", 2, 32))
        assertFalse(base == derive("password", "salt", 3, 32))
    }

    @Test
    fun theInputsAreNotModified() {
        val password = "password".toByteArray()
        val salt = "salt".toByteArray()
        BcryptPbkdf.derive(password, salt, 1, 32)
        assertArrayEquals("password".toByteArray(), password)
        assertArrayEquals("salt".toByteArray(), salt)
    }

    @Test
    fun nonsenseParametersAreRefused() {
        val one = byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) {
            BcryptPbkdf.derive(ByteArray(0), one, 1, 32)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BcryptPbkdf.derive(one, ByteArray(0), 1, 32)
        }
        assertThrows(IllegalArgumentException::class.java) { BcryptPbkdf.derive(one, one, 0, 32) }
        assertThrows(IllegalArgumentException::class.java) { BcryptPbkdf.derive(one, one, 1, 0) }
    }
}
