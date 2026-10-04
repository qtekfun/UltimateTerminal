// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.ssh

import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.fakes.testSecretBox
import java.security.GeneralSecurityException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What a locked or invalidated Android Keystore key throws; it is a GeneralSecurityException. */
private class KeystoreLocked : GeneralSecurityException("keystore locked")

class AesGcmSecretBoxTest {
    private val box = testSecretBox()
    private val secret = "-----BEGIN OPENSSH PRIVATE KEY-----".toByteArray()

    private fun sealed(context: String = "k1"): ByteArray =
        (box.seal(secret, context) as SshResult.Success).value

    private fun opened(
        blob: ByteArray,
        context: String = "k1",
        with: AesGcmSecretBox = box
    ): SshResult<ByteArray> = with.open(blob, context)

    @Test
    fun whatIsSealedOpensAgain() {
        assertArrayEquals(secret, (opened(sealed()) as SshResult.Success).value)
    }

    @Test
    fun theSealedBytesDoNotContainThePlainText() {
        val blob = sealed()
        val text = String(blob, Charsets.ISO_8859_1)
        assertFalse(text.contains("OPENSSH"))
        assertEquals(2 + 12 + secret.size + 16, blob.size)
    }

    @Test
    fun theLayoutIsVersionIvLengthIvThenCiphertext() {
        val blob = sealed()
        assertEquals(1, blob[0].toInt())
        assertEquals(12, blob[1].toInt())
    }

    @Test
    fun everySealUsesAFreshIv() {
        assertNotEquals(sealed().toList(), sealed().toList())
    }

    @Test
    fun aBlobCannotBeMovedToAnotherContext() {
        assertEquals(SshResult.Failure(SshError.CorruptKey), opened(sealed("k1"), "k2"))
    }

    @Test
    fun aDifferentKeyCannotOpenIt() {
        val other = testSecretBox(keyByte = 9)
        assertEquals(SshResult.Failure(SshError.CorruptKey), opened(sealed(), with = other))
    }

    @Test
    fun anyChangedByteIsDetected() {
        val blob = sealed()
        for (index in listOf(0, 1, 2, 13, 14, blob.size - 1)) {
            val tampered = blob.copyOf().also { it[index] = (it[index] + 1).toByte() }
            assertEquals(SshResult.Failure(SshError.CorruptKey), opened(tampered), "byte $index")
        }
    }

    @Test
    fun truncatedOrEmptyInputIsCorruptNotACrash() {
        val blob = sealed()
        listOf(0, 1, 2, 5, 13, 14, blob.size - 1).forEach { length ->
            assertEquals(
                SshResult.Failure(SshError.CorruptKey),
                opened(blob.copyOf(length)),
                "length $length"
            )
        }
    }

    @Test
    fun anUnknownVersionOrIvLengthIsCorrupt() {
        val wrongVersion = sealed().also { it[0] = 2 }
        assertEquals(SshResult.Failure(SshError.CorruptKey), opened(wrongVersion))
        val wrongIv = sealed().also { it[1] = 8 }
        assertEquals(SshResult.Failure(SshError.CorruptKey), opened(wrongIv))
    }

    @Test
    fun aKeyThatCannotBeUsedFailsCleanlyOnBothSides() {
        val broken = AesGcmSecretBox { throw KeystoreLocked() }
        assertTrue(broken.seal(secret, "k1") is SshResult.Failure)
        assertEquals(SshResult.Failure(SshError.CorruptKey), broken.open(sealed(), "k1"))
    }

    @Test
    fun anEmptySecretStillRoundTrips() {
        val blob = (box.seal(ByteArray(0), "k1") as SshResult.Success).value
        assertEquals(0, (box.open(blob, "k1") as SshResult.Success).value.size)
    }
}
