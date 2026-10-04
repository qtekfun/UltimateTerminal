// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.ssh

import com.qtekfun.ultimateterminal.domain.ssh.OpenSshKeyFormat
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshPrivateKey
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcaSshKeyGeneratorTest {
    private val generator = JcaSshKeyGenerator(rsaBits = 2048)

    private fun key(type: SshKeyType): SshPrivateKey =
        (generator.generate(type) as SshResult.Success).value

    @Test
    fun bothTypesAreOfferedWhereThePlatformHasThem() {
        assertTrue(SshKeyType.RSA in generator.supportedTypes)
        assertTrue(SshKeyType.ED25519 in generator.supportedTypes)
    }

    @Test
    fun anRsaKeyIsMathematicallyConsistent() {
        val rsa = key(SshKeyType.RSA) as SshPrivateKey.Rsa
        assertEquals(rsa.n, rsa.p * rsa.q)
        assertEquals(2048, rsa.n.bitLength())
        assertEquals(BigInteger.valueOf(65_537), rsa.e)
        // The defining property of an RSA key pair: what the public key encrypts, the private
        // exponent decrypts.
        val message = BigInteger.valueOf(0x1234_5678_9abcL)
        assertEquals(message, message.modPow(rsa.e, rsa.n).modPow(rsa.d, rsa.n))
        assertEquals(BigInteger.ONE, (rsa.q * rsa.iqmp).mod(rsa.p))
    }

    @Test
    fun theRawEd25519BytesAreTheRealSeedAndPublicKey() {
        val ed = key(SshKeyType.ED25519) as SshPrivateKey.Ed25519
        assertEquals(32, ed.seed.size)
        assertEquals(32, ed.publicKey.size)
        // PKCS#8 and X.509 headers for Ed25519: rebuild both keys from the raw bytes and check a
        // signature made with one verifies with the other, so the pair really belongs together.
        val privateHeader =
            byteArrayOf(
                0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
                0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
            )
        val publicHeader =
            byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
        val factory = KeyFactory.getInstance("Ed25519")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(privateHeader + ed.seed))
        val public = factory.generatePublic(X509EncodedKeySpec(publicHeader + ed.publicKey))
        val message = "hello".toByteArray()
        val signature = Signature.getInstance("Ed25519").run {
            initSign(private)
            update(message)
            sign()
        }
        val valid = Signature.getInstance("Ed25519").run {
            initVerify(public)
            update(message)
            verify(signature)
        }
        assertTrue(valid)
    }

    @Test
    fun everyGeneratedKeyIsDifferent() {
        assertNotEquals(
            OpenSshKeyFormat.fingerprint(key(SshKeyType.ED25519)),
            OpenSshKeyFormat.fingerprint(key(SshKeyType.ED25519))
        )
    }

    @Test
    fun generatedKeysSurviveTheOpenSshFileRoundTrip() {
        SshKeyType.entries.forEach { type ->
            val original = key(type)
            val text = OpenSshKeyFormat.encodePrivate(original, "c")
            val back = (OpenSshKeyFormat.parsePrivate(text) as SshResult.Success).value
            assertEquals(
                OpenSshKeyFormat.fingerprint(original),
                OpenSshKeyFormat.fingerprint(back),
                type.name
            )
        }
    }
}
