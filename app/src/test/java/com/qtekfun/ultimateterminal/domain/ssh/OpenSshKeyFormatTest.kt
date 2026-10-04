// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenSshKeyFormatTest {
    private fun ed25519(): SshPrivateKey.Ed25519 {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val secret = pair.private.encoded
        val publicKey = pair.public.encoded
        return SshPrivateKey.Ed25519(
            secret.copyOfRange(secret.size - 32, secret.size),
            publicKey.copyOfRange(publicKey.size - 32, publicKey.size)
        )
    }

    private fun rsa(bits: Int = 2048): SshPrivateKey.Rsa {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }
        val key = generator.generateKeyPair().private as RSAPrivateCrtKey
        return SshPrivateKey.Rsa(
            key.modulus,
            key.publicExponent,
            key.privateExponent,
            key.primeP,
            key.primeQ,
            key.crtCoefficient
        )
    }

    private fun parsed(text: String): SshPrivateKey =
        (OpenSshKeyFormat.parsePrivate(text) as SshResult.Success).value

    private fun error(text: String): SshError =
        (OpenSshKeyFormat.parsePrivate(text) as SshResult.Failure).error

    // Wire-format helpers to build hostile or unusual files by hand.
    private fun wire(block: ByteArrayOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().apply(block).toByteArray()

    private fun ByteArrayOutputStream.int(value: Int) {
        write(
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte()
            )
        )
    }

    private fun ByteArrayOutputStream.str(value: ByteArray) {
        int(value.size)
        write(value)
    }

    private fun ByteArrayOutputStream.str(value: String) = str(value.toByteArray())

    private fun frame(file: ByteArray): String = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
        Base64.getEncoder().encodeToString(file) + "\n-----END OPENSSH PRIVATE KEY-----\n"

    private fun header(
        cipher: String = "none",
        kdf: String = "none",
        count: Int = 1,
        rest: ByteArrayOutputStream.() -> Unit
    ) = wire {
        write("openssh-key-v1\u0000".toByteArray())
        str(cipher)
        str(kdf)
        str("")
        int(count)
        rest()
    }

    @Test
    fun anEd25519KeySurvivesTheRoundTrip() {
        val key = ed25519()
        val back = parsed(OpenSshKeyFormat.encodePrivate(key, "me@phone")) as SshPrivateKey.Ed25519
        assertArrayEquals(key.seed, back.seed)
        assertArrayEquals(key.publicKey, back.publicKey)
    }

    @Test
    fun anRsaKeySurvivesTheRoundTrip() {
        val key = rsa()
        val back = parsed(OpenSshKeyFormat.encodePrivate(key, "me@phone")) as SshPrivateKey.Rsa
        assertEquals(
            listOf(key.n, key.e, key.d, key.p, key.q, key.iqmp),
            listOf(back.n, back.e, back.d, back.p, back.q, back.iqmp)
        )
    }

    @Test
    fun theFileHasTheOpenSshFraming() {
        val text = OpenSshKeyFormat.encodePrivate(ed25519(), "c")
        assertTrue(text.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----\n"))
        assertTrue(text.endsWith("-----END OPENSSH PRIVATE KEY-----\n"))
        assertTrue(text.lines().drop(1).dropLast(2).all { it.length <= 70 })
        val body = text.lines().drop(1).dropLast(2).joinToString("")
        // Decoded, the file starts with OpenSSH's magic, which `ssh` checks before anything else.
        val magic = Base64.getDecoder().decode(body).copyOfRange(0, 15)
        assertArrayEquals("openssh-key-v1\u0000".toByteArray(), magic)
    }

    @Test
    fun thePublicLineNamesTheTypeAndCarriesTheBlob() {
        val key = ed25519()
        val line = OpenSshKeyFormat.publicLine(key, "me@phone")
        val (type, blob, comment) = line.split(" ")
        assertEquals("ssh-ed25519", type)
        assertEquals("me@phone", comment)
        // string "ssh-ed25519" (4 + 11) then string of 32 bytes (4 + 32)
        assertEquals(51, Base64.getDecoder().decode(blob).size)
    }

    @Test
    fun theCommentCannotBreakTheLine() {
        val line = OpenSshKeyFormat.publicLine(ed25519(), "a\nb\u0000c")
        assertEquals(1, line.lines().size)
        assertFalse(line.any { it.isISOControl() })
    }

    @Test
    fun aMissingCommentLeavesTwoFields() {
        assertEquals(2, OpenSshKeyFormat.publicLine(ed25519(), "  ").split(" ").size)
    }

    @Test
    fun theFingerprintIsSha256InUnpaddedBase64() {
        val fingerprint = OpenSshKeyFormat.fingerprint(ed25519())
        assertTrue(fingerprint.startsWith("SHA256:"))
        assertEquals(43, fingerprint.removePrefix("SHA256:").length)
        assertFalse(fingerprint.endsWith("="))
    }

    @Test
    fun theSameKeyAlwaysGivesTheSameFileAndFingerprint() {
        val key = ed25519()
        assertEquals(
            OpenSshKeyFormat.encodePrivate(key, "c"),
            OpenSshKeyFormat.encodePrivate(key, "c")
        )
        assertEquals(OpenSshKeyFormat.fingerprint(key), OpenSshKeyFormat.fingerprint(key))
    }

    @Test
    fun aPassphraseProtectedKeyIsRefusedAsEncrypted() {
        val file = header(cipher = "aes256-ctr", kdf = "bcrypt") {
            str(ByteArray(8))
            str(ByteArray(8))
        }
        assertEquals(SshError.EncryptedKey, error(frame(file)))
    }

    @Test
    fun otherKeyTypesAreUnsupportedNotCorrupt() {
        val section = wire {
            int(1)
            int(1)
            str("ecdsa-sha2-nistp256")
        }
        val file = header {
            str(ByteArray(4))
            str(section)
        }
        assertEquals(SshError.UnsupportedKeyFormat, error(frame(file)))
    }

    @Test
    fun moreThanOneKeyInTheFileIsUnsupported() {
        assertEquals(
            SshError.UnsupportedKeyFormat,
            error(
                frame(
                    header(count = 2) {
                        str(ByteArray(4))
                        str(ByteArray(4))
                    }
                )
            )
        )
    }

    @Test
    fun textWithoutTheFramingIsNotAKey() {
        assertEquals(SshError.UnsupportedKeyFormat, error("hello"))
        assertEquals(SshError.UnsupportedKeyFormat, error(""))
        assertEquals(
            SshError.UnsupportedKeyFormat,
            error("-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA")
        )
    }

    @Test
    fun damagedBytesAreCorruptNeverACrash() {
        val good = OpenSshKeyFormat.encodePrivate(ed25519(), "c")
        val body = good.lines().drop(1).dropLast(2).joinToString("")
        val bytes = Base64.getDecoder().decode(body)
        // every truncation of a good file must fail cleanly
        for (length in listOf(0, 3, 14, 15, 20, 40, bytes.size - 1)) {
            assertEquals(SshError.CorruptKey, error(frame(bytes.copyOf(length))), "length $length")
        }
        assertEquals(
            SshError.CorruptKey,
            error("-----BEGIN OPENSSH PRIVATE KEY-----\n!!!!\n-----END OPENSSH PRIVATE KEY-----")
        )
    }

    @Test
    fun aLengthLargerThanTheFileIsCorrupt() {
        val file = wire {
            write("openssh-key-v1\u0000".toByteArray())
            int(Int.MAX_VALUE)
        }
        assertEquals(SshError.CorruptKey, error(frame(file)))
        val negative = wire {
            write("openssh-key-v1\u0000".toByteArray())
            int(-1)
        }
        assertEquals(SshError.CorruptKey, error(frame(negative)))
    }

    @Test
    fun checkIntegersThatDifferMeanTheFileIsDamaged() {
        val section = wire {
            int(1)
            int(2)
            str("ssh-ed25519")
        }
        assertEquals(
            SshError.CorruptKey,
            error(
                frame(
                    header {
                        str(ByteArray(4))
                        str(section)
                    }
                )
            )
        )
    }

    @Test
    fun anEd25519PairWhoseHalvesDisagreeIsCorrupt() {
        val publicKey = ByteArray(32) { 1 }
        val section = wire {
            int(5)
            int(5)
            str("ssh-ed25519")
            str(publicKey)
            str(ByteArray(32) { 9 } + ByteArray(32) { 2 })
            str("c")
        }
        assertEquals(
            SshError.CorruptKey,
            error(
                frame(
                    header {
                        str(ByteArray(4))
                        str(section)
                    }
                )
            )
        )
    }

    @Test
    fun anEd25519KeyOfTheWrongSizeIsCorrupt() {
        val section = wire {
            int(5)
            int(5)
            str("ssh-ed25519")
            str(ByteArray(31))
            str(ByteArray(64))
            str("c")
        }
        assertEquals(
            SshError.CorruptKey,
            error(
                frame(
                    header {
                        str(ByteArray(4))
                        str(section)
                    }
                )
            )
        )
    }

    @Test
    fun anRsaKeyWhosePrimesDoNotMakeTheModulusIsCorrupt() {
        fun ByteArrayOutputStream.mp(value: Long) = str(BigInteger.valueOf(value).toByteArray())
        val section = wire {
            int(5)
            int(5)
            str("ssh-rsa")
            mp(35)
            mp(3)
            mp(7)
            mp(2)
            mp(5)
            mp(8)
            str("c")
        }
        assertEquals(
            SshError.CorruptKey,
            error(
                frame(
                    header {
                        str(ByteArray(4))
                        str(section)
                    }
                )
            )
        )
    }

    @Test
    fun anRsaKeyWhosePrimesMakeTheModulusParses() {
        fun ByteArrayOutputStream.mp(value: Long) = str(BigInteger.valueOf(value).toByteArray())
        val section = wire {
            int(5)
            int(5)
            str("ssh-rsa")
            mp(35)
            mp(3)
            mp(7)
            mp(2)
            mp(5)
            mp(7)
            str("c")
        }
        val key = parsed(
            frame(
                header {
                    str(ByteArray(4))
                    str(section)
                }
            )
        )
        assertInstanceOf(SshPrivateKey.Rsa::class.java, key)
    }

    @Test
    fun keysDoNotPrintTheirSecretsInToString() {
        val key = ed25519()
        assertFalse(key.toString().contains(Base64.getEncoder().encodeToString(key.seed)))
        assertFalse(key.toString().contains(key.seed.joinToString(",")))
        val rsa = rsa(1024)
        assertFalse(rsa.toString().contains(rsa.d.toString()))
    }
}
