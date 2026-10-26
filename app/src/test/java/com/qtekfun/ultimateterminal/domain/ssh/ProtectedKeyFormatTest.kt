// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtectedKeyFormatTest {
    private val ed = SshPrivateKey.Ed25519(ByteArray(32) { (it + 1).toByte() }, ByteArray(32) { 7 })
    private val rsa = SshPrivateKey.Rsa(
        n = BigInteger.valueOf(3233),
        e = BigInteger.valueOf(17),
        d = BigInteger.valueOf(2753),
        p = BigInteger.valueOf(61),
        q = BigInteger.valueOf(53),
        iqmp = BigInteger.valueOf(38)
    )
    private val pass = "hunter22".toByteArray()

    // Few rounds keep the tests fast; the default is exercised once.
    private fun protect(key: SshPrivateKey, passphrase: ByteArray = pass, rounds: Int = 2) =
        OpenSshKeyFormat.encodePrivate(key, "c", passphrase, rounds)

    private fun error(result: SshResult<*>) = (result as SshResult.Failure).error

    private fun ok(result: SshResult<SshPrivateKey>) = (result as SshResult.Success).value

    // Rewrites the header of a protected key file: swaps the first occurrence of one field value
    // for another of the same length, so offsets stay valid.
    private fun patch(pem: String, from: String, to: String): String {
        val body = pem.lines().filterNot { it.startsWith("-----") }.joinToString("")
        val raw = Base64.getDecoder().decode(body)
        val text = String(raw, Charsets.ISO_8859_1).replaceFirst(from, to)
        val encoded = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.ISO_8859_1))
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n$encoded\n-----END OPENSSH PRIVATE KEY-----\n"
    }

    @Test
    fun anEd25519KeyRoundTripsThroughAPassphrase() {
        val text = protect(ed)
        assertTrue(OpenSshKeyFormat.isProtected(text))
        val back = ok(OpenSshKeyFormat.parsePrivate(text, pass)) as SshPrivateKey.Ed25519
        assertArrayEquals(ed.seed, back.seed)
        assertArrayEquals(ed.publicKey, back.publicKey)
    }

    @Test
    fun anRsaKeyRoundTripsThroughAPassphrase() {
        val back = ok(OpenSshKeyFormat.parsePrivate(protect(rsa), pass)) as SshPrivateKey.Rsa
        assertEquals(rsa.n, back.n)
        assertEquals(rsa.d, back.d)
        assertEquals(rsa.iqmp, back.iqmp)
    }

    @Test
    fun theDefaultNumberOfRoundsWorks() {
        val text = OpenSshKeyFormat.encodePrivate(ed, "c", pass)
        assertTrue(OpenSshKeyFormat.parsePrivate(text, pass) is SshResult.Success)
    }

    @Test
    fun theProtectedFileHidesTheSecretAndKeepsThePublicKey() {
        val text = protect(ed)
        val raw = Base64.getDecoder().decode(
            text.lines().filterNot { it.startsWith("-----") }.joinToString("")
        )
        val seed = String(ed.seed, Charsets.ISO_8859_1)
        assertFalse(String(raw, Charsets.ISO_8859_1).contains(seed))
        val plain = OpenSshKeyFormat.encodePrivate(ed, "c")
        assertFalse(OpenSshKeyFormat.isProtected(plain))
        assertNotEquals(plain, text)
    }

    @Test
    fun everyFileGetsItsOwnSalt() {
        assertNotEquals(protect(ed), protect(ed))
    }

    @Test
    fun aWrongPassphraseIsToldApartFromADamagedKey() {
        val text = protect(ed)
        assertEquals(
            SshError.WrongPassphrase,
            error(OpenSshKeyFormat.parsePrivate(text, "hunter23".toByteArray()))
        )
    }

    @Test
    fun noPassphraseAsksForOne() {
        val text = protect(ed)
        assertEquals(SshError.PassphraseRequired, error(OpenSshKeyFormat.parsePrivate(text)))
        assertEquals(
            SshError.PassphraseRequired,
            error(OpenSshKeyFormat.parsePrivate(text, ByteArray(0)))
        )
    }

    @Test
    fun aPassphraseGivenForAnUnprotectedKeyIsIgnored() {
        val plain = OpenSshKeyFormat.encodePrivate(ed, "c")
        assertTrue(OpenSshKeyFormat.parsePrivate(plain, pass) is SshResult.Success)
    }

    @Test
    fun aTruncatedProtectedKeyIsCorrupt() {
        val text = protect(ed)
        val body = text.lines().filterNot { it.startsWith("-----") }.joinToString("")
        val cut = "-----BEGIN OPENSSH PRIVATE KEY-----\n${body.take(body.length - 40)}\n" +
            "-----END OPENSSH PRIVATE KEY-----\n"
        assertEquals(SshError.CorruptKey, error(OpenSshKeyFormat.parsePrivate(cut, pass)))
    }

    @Test
    fun aCipherTheAppDoesNotKnowIsUnsupported() {
        val text = patch(protect(ed), "aes256-ctr", "aes256-xyz")
        assertEquals(
            SshError.UnsupportedKeyFormat,
            error(OpenSshKeyFormat.parsePrivate(text, pass))
        )
    }

    @Test
    fun aKdfTheAppDoesNotKnowIsUnsupported() {
        val text = patch(protect(ed), "bcrypt", "scrypt")
        assertEquals(
            SshError.UnsupportedKeyFormat,
            error(OpenSshKeyFormat.parsePrivate(text, pass))
        )
    }

    @Test
    fun absurdRoundsAreRefusedInsteadOfHangingThePhone() {
        // 2 rounds, written big-endian right after the 16-byte salt, become 1,000,000.
        val pem = protect(ed)
        val body = pem.lines().filterNot { it.startsWith("-----") }.joinToString("")
        val raw = Base64.getDecoder().decode(body)
        val at =
            String(raw, Charsets.ISO_8859_1).indexOf("bcrypt") + "bcrypt".length + 4 + 4 + 4 + 16
        raw[at + 1] = 0x0F
        raw[at + 2] = 0x42
        raw[at + 3] = 0x40
        val text =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(raw) +
                "\n-----END OPENSSH PRIVATE KEY-----\n"
        assertEquals(
            SshError.UnsupportedKeyFormat,
            error(OpenSshKeyFormat.parsePrivate(text, pass))
        )
    }

    @Test
    fun theSaltComesFromTheGivenRandomSource() {
        val one = OpenSshKeyFormat.encodePrivate(ed, "c", pass, 1, SecureRandom())
        assertTrue(one.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
    }

    @Test
    fun anUnprotectedKeyIsNotProtectedAndGarbageIsNot() {
        assertFalse(OpenSshKeyFormat.isProtected(OpenSshKeyFormat.encodePrivate(ed, "c")))
        assertFalse(OpenSshKeyFormat.isProtected("hello"))
        assertFalse(
            OpenSshKeyFormat.isProtected(
                "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----"
            )
        )
    }

    // Keys written by the real ssh-keygen (OpenSSH 10.2): the whole chain, from the passphrase to
    // the seed, has to match what OpenSSH does, or none of these would open.
    @Test
    fun opensSshKeygenKeysProtectedWithAes256Ctr() = assertOpens(ProtectedKeyFixtures.ctr)

    @Test
    fun opensSshKeygenKeysProtectedWithAes256Cbc() = assertOpens(ProtectedKeyFixtures.cbc)

    @Test
    fun opensSshKeygenKeysWhosePassphraseIsNotAscii() = assertOpens(ProtectedKeyFixtures.unicode)

    private fun assertOpens(vector: ProtectedKeyFixtures.Vector) {
        val pem = vector.pem()
        assertTrue(OpenSshKeyFormat.isProtected(pem))
        assertEquals(SshError.PassphraseRequired, error(OpenSshKeyFormat.parsePrivate(pem)))
        assertEquals(
            SshError.WrongPassphrase,
            error(OpenSshKeyFormat.parsePrivate(pem, "wrong".toByteArray()))
        )
        val key = ok(OpenSshKeyFormat.parsePrivate(pem, vector.passphrase.toByteArray()))
        assertEquals(vector.fingerprint, OpenSshKeyFormat.fingerprint(key))
        assertEquals(
            vector.publicBlob,
            Base64.getEncoder().encodeToString(OpenSshKeyFormat.publicBlob(key))
        )
    }
}
