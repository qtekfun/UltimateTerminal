// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrivateKeyTextTest {
    private val key: RSAPrivateCrtKey = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }.generateKeyPair().private as RSAPrivateCrtKey

    private fun pem(label: String, der: ByteArray): String = "-----BEGIN $label-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
        "\n-----END $label-----\n"

    private fun der(tag: Int, content: ByteArray): ByteArray {
        val length = content.size
        val header = when {
            length < 0x80 -> byteArrayOf(tag.toByte(), length.toByte())

            length < 0x100 -> byteArrayOf(tag.toByte(), 0x81.toByte(), length.toByte())

            else -> byteArrayOf(
                tag.toByte(),
                0x82.toByte(),
                (length shr 8).toByte(),
                length.toByte()
            )
        }
        return header + content
    }

    private fun pkcs1(numbers: List<BigInteger>): ByteArray {
        val body = ByteArrayOutputStream()
        numbers.forEach { body.write(der(0x02, it.toByteArray())) }
        return der(0x30, body.toByteArray())
    }

    private fun rsaNumbers() = listOf(
        BigInteger.ZERO, key.modulus, key.publicExponent, key.privateExponent, key.primeP,
        key.primeQ, key.primeExponentP, key.primeExponentQ, key.crtCoefficient
    )

    private fun ok(text: String): SshPrivateKey =
        (PrivateKeyText.parse(text) as SshResult.Success).value

    private fun error(text: String): SshError =
        (PrivateKeyText.parse(text) as SshResult.Failure).error

    @Test
    fun anRsaKeyInPkcs8IsRead() {
        val rsa = ok(pem("PRIVATE KEY", key.encoded)) as SshPrivateKey.Rsa
        assertEquals(key.modulus, rsa.n)
        assertEquals(key.privateExponent, rsa.d)
        assertEquals(key.crtCoefficient, rsa.iqmp)
    }

    @Test
    fun anRsaKeyInPkcs1IsRead() {
        val rsa = ok(pem("RSA PRIVATE KEY", pkcs1(rsaNumbers()))) as SshPrivateKey.Rsa
        assertEquals(
            listOf(key.modulus, key.publicExponent, key.privateExponent),
            listOf(rsa.n, rsa.e, rsa.d)
        )
        assertEquals(
            listOf(key.primeP, key.primeQ, key.crtCoefficient),
            listOf(rsa.p, rsa.q, rsa.iqmp)
        )
    }

    @Test
    fun aLongFormDerLengthIsHandled() {
        // A 2048-bit key's sequence is longer than 255 bytes, so this already uses the 2-byte form;
        // check the parsed key is complete rather than truncated.
        val rsa = ok(pem("RSA PRIVATE KEY", pkcs1(rsaNumbers()))) as SshPrivateKey.Rsa
        assertEquals(key.modulus.bitLength(), rsa.n.bitLength())
    }

    private fun rsaKey() = SshPrivateKey.Rsa(
        key.modulus,
        key.publicExponent,
        key.privateExponent,
        key.primeP,
        key.primeQ,
        key.crtCoefficient
    )

    @Test
    fun anOpenSshKeyIsHandedToTheOpenSshReader() {
        val text = OpenSshKeyFormat.encodePrivate(rsaKey(), "c")
        val back = ok(text) as SshPrivateKey.Rsa
        assertEquals(key.modulus, back.n)
    }

    @Test
    fun anOpenSshKeyThatIsDamagedKeepsItsOwnError() {
        val text = "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----\n"
        assertEquals(SshError.CorruptKey, error(text))
    }

    @Test
    fun anEncryptedPkcs8KeyIsRefusedAsEncrypted() {
        assertEquals(SshError.EncryptedKey, error(pem("ENCRYPTED PRIVATE KEY", ByteArray(40))))
    }

    @Test
    fun anEncryptedLegacyPemKeyIsRefusedAsEncrypted() {
        val text = listOf(
            "-----BEGIN RSA PRIVATE KEY-----",
            "Proc-Type: 4,ENCRYPTED",
            "DEK-Info: AES-128-CBC,00",
            "",
            "AAAA",
            "-----END RSA PRIVATE KEY-----"
        ).joinToString("\n")
        assertEquals(SshError.EncryptedKey, error(text))
    }

    @Test
    fun otherLabelsAndPlainTextAreUnsupported() {
        assertEquals(SshError.UnsupportedKeyFormat, error(pem("EC PRIVATE KEY", ByteArray(40))))
        assertEquals(SshError.UnsupportedKeyFormat, error(pem("CERTIFICATE", ByteArray(40))))
        assertEquals(SshError.UnsupportedKeyFormat, error("just some text"))
        assertEquals(SshError.UnsupportedKeyFormat, error(""))
    }

    @Test
    fun pkcs8ThatIsNotRsaIsUnsupported() {
        val ed = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().private.encoded
        assertEquals(SshError.UnsupportedKeyFormat, error(pem("PRIVATE KEY", ed)))
    }

    @Test
    fun garbageInsideAKnownFrameIsRejectedWithoutACrash() {
        assertEquals(
            SshError.UnsupportedKeyFormat,
            error(
                pem(
                    "PRIVATE KEY",
                    ByteArray(20) {
                        it.toByte()
                    }
                )
            )
        )
        assertEquals(
            SshError.CorruptKey,
            error("-----BEGIN RSA PRIVATE KEY-----\n@@@\n-----END RSA PRIVATE KEY-----\n")
        )
        assertEquals(
            SshError.CorruptKey,
            error(
                pem(
                    "RSA PRIVATE KEY",
                    ByteArray(10) {
                        it.toByte()
                    }
                )
            )
        )
    }

    @Test
    fun aPkcs1KeyWithTooFewFieldsOrANonZeroVersionIsCorrupt() {
        assertEquals(
            SshError.CorruptKey,
            error(pem("RSA PRIVATE KEY", pkcs1(rsaNumbers().take(5))))
        )
        val badVersion = listOf(BigInteger.ONE) + rsaNumbers().drop(1)
        assertEquals(SshError.CorruptKey, error(pem("RSA PRIVATE KEY", pkcs1(badVersion))))
    }

    @Test
    fun aTruncatedOrOverlongDerSequenceIsCorrupt() {
        val good = pkcs1(rsaNumbers())
        assertEquals(SshError.CorruptKey, error(pem("RSA PRIVATE KEY", good.copyOf(good.size - 5))))
        // the declared length claims more bytes than there are
        val lying = good.copyOf()
        lying[3] = (lying[3] + 1).toByte()
        assertEquals(SshError.CorruptKey, error(pem("RSA PRIVATE KEY", lying)))
        // an INTEGER whose tag is wrong
        val wrongTag = good.copyOf()
        wrongTag[4] = 0x04
        assertEquals(SshError.CorruptKey, error(pem("RSA PRIVATE KEY", wrongTag)))
    }
}
