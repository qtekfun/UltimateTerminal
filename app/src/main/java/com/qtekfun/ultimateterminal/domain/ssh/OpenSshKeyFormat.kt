// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Reads and writes the OpenSSH key formats (the `openssh-key-v1` private key file and the
 * `authorized_keys` public line), for Ed25519 and RSA. A private key is written either unencrypted
 * or protected by a passphrase, as `ssh-keygen` does it (AES-256-CTR keyed by `bcrypt_pbkdf`); in
 * both cases it is also sealed by a [SecretBox] at rest.
 */
object OpenSshKeyFormat {
    private const val BEGIN = "-----BEGIN OPENSSH PRIVATE KEY-----"
    private const val END = "-----END OPENSSH PRIVATE KEY-----"
    internal val MAGIC = "openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII)
    private const val NONE = "none"
    private const val LINE_LENGTH = 70
    private const val BLOCK = 8
    private const val BCRYPT = "bcrypt"
    private const val SALT_BYTES = 16

    /** `ssh-keygen -a`'s default: about a fifth of a second on a phone. */
    const val DEFAULT_ROUNDS = 16

    fun publicBlob(key: SshPrivateKey): ByteArray = SshWriter().apply {
        string(key.type.sshName)
        when (key) {
            is SshPrivateKey.Ed25519 -> bytes(key.publicKey)

            is SshPrivateKey.Rsa -> {
                mpint(key.e)
                mpint(key.n)
            }
        }
    }.toByteArray()

    /** The `authorized_keys` line: `<type> <base64 blob> <comment>`. */
    fun publicLine(key: SshPrivateKey, comment: String): String {
        val blob = Base64.getEncoder().encodeToString(publicBlob(key))
        val clean = comment.filter { !it.isISOControl() }.trim()
        val line = "${key.type.sshName} $blob"
        return if (clean.isEmpty()) line else "$line $clean"
    }

    /** `SHA256:<unpadded base64>`, the fingerprint `ssh-keygen -l` shows. */
    fun fingerprint(key: SshPrivateKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicBlob(key))
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Writes an `openssh-key-v1` file: unencrypted if [passphrase] is null, otherwise encrypted with
     * it, using [rounds] of `bcrypt_pbkdf` and a fresh random salt from [random].
     */
    fun encodePrivate(
        key: SshPrivateKey,
        comment: String,
        passphrase: ByteArray? = null,
        rounds: Int = DEFAULT_ROUNDS,
        random: SecureRandom = SecureRandom()
    ): String {
        val cipher = if (passphrase == null) null else KeyCipher.default
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val block = cipher?.blockSize ?: BLOCK
        val section = privateSection(key, comment)
        var padding = 1
        while (section.size() % block != 0) section.raw(byteArrayOf((padding++).toByte()))
        val plain = section.toByteArray()
        val body = if (cipher == null || passphrase == null) {
            plain
        } else {
            cipher.encrypt(passphrase, salt, rounds, plain).also { plain.fill(0) }
        }
        val file = SshWriter().apply {
            raw(MAGIC)
            string(if (cipher == null) NONE else KeyCipher.DEFAULT_NAME)
            string(if (cipher == null) NONE else BCRYPT)
            bytes(
                if (cipher == null) {
                    ByteArray(0)
                } else {
                    SshWriter().apply {
                        bytes(salt)
                        int(rounds)
                    }.toByteArray()
                }
            )
            int(1)
            bytes(publicBlob(key))
            bytes(body)
        }.toByteArray()
        val armored = Base64.getEncoder().encodeToString(
            file
        ).chunked(LINE_LENGTH).joinToString("\n")
        return "$BEGIN\n$armored\n$END\n"
    }

    // The private section before padding: the check words, the key and its comment.
    private fun privateSection(key: SshPrivateKey, comment: String): SshWriter {
        val check = checkInt(key)
        return SshWriter().apply {
            int(check)
            int(check)
            string(key.type.sshName)
            when (key) {
                is SshPrivateKey.Ed25519 -> {
                    bytes(key.publicKey)
                    bytes(key.seed + key.publicKey)
                }

                is SshPrivateKey.Rsa -> {
                    mpint(key.n)
                    mpint(key.e)
                    mpint(key.d)
                    mpint(key.iqmp)
                    mpint(key.p)
                    mpint(key.q)
                }
            }
            string(comment)
        }
    }

    /**
     * Reads an OpenSSH private key. A protected key needs its [passphrase] (UTF-8 bytes): without it
     * the result is [SshError.PassphraseRequired], with a wrong one [SshError.WrongPassphrase]. Anything
     * damaged gives [SshError.CorruptKey]; other key types (ECDSA, certificates) and ciphers give
     * [SshError.UnsupportedKeyFormat].
     */
    fun parsePrivate(text: String, passphrase: ByteArray? = null): SshResult<SshPrivateKey> {
        val body = framedBody(text)
        return if (body == null) {
            SshResult.Failure(SshError.UnsupportedKeyFormat)
        } else {
            OpenSshKeyReader.decode(body, passphrase)
        }
    }

    /** Whether [text] is an OpenSSH private key that needs a passphrase to be read. */
    fun isProtected(text: String): Boolean {
        val cipher = framedBody(text)?.let(OpenSshKeyReader::cipherName)
        return cipher != null && cipher != NONE
    }

    private fun framedBody(text: String): String? {
        val trimmed = text.trim()
        return if (trimmed.startsWith(BEGIN) && trimmed.endsWith(END)) {
            trimmed.removePrefix(BEGIN).removeSuffix(END)
        } else {
            null
        }
    }

    // The two check integers only have to be equal; derive them from the key so the output is
    // stable for a given key, which keeps the tests deterministic.
    private fun checkInt(key: SshPrivateKey): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicBlob(key))
        return digest.take(INT_BYTES).fold(0) { acc, b ->
            (acc shl BYTE_BITS) or
                (b.toInt() and BYTE_MASK)
        }
    }
}
