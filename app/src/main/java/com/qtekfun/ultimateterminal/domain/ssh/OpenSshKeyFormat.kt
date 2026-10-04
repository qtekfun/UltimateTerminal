// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.security.MessageDigest
import java.util.Base64

/**
 * Reads and writes the OpenSSH key formats (the `openssh-key-v1` private key file and the
 * `authorized_keys` public line), for Ed25519 and RSA. Private keys are written unencrypted: at
 * rest they are sealed by a [SecretBox], and in the distro the file is private to the app.
 */
object OpenSshKeyFormat {
    private const val BEGIN = "-----BEGIN OPENSSH PRIVATE KEY-----"
    private const val END = "-----END OPENSSH PRIVATE KEY-----"
    internal val MAGIC = "openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII)
    private const val NONE = "none"
    private const val LINE_LENGTH = 70
    private const val BLOCK = 8

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

    /** Writes an unencrypted `openssh-key-v1` file. */
    fun encodePrivate(key: SshPrivateKey, comment: String): String {
        val check = checkInt(key)
        val section = SshWriter().apply {
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
        var padding = 1
        while (section.size() % BLOCK != 0) section.raw(byteArrayOf((padding++).toByte()))
        val file = SshWriter().apply {
            raw(MAGIC)
            string(NONE)
            string(NONE)
            string("")
            int(1)
            bytes(publicBlob(key))
            bytes(section.toByteArray())
        }.toByteArray()
        val body = Base64.getEncoder().encodeToString(file).chunked(LINE_LENGTH).joinToString("\n")
        return "$BEGIN\n$body\n$END\n"
    }

    /**
     * Reads an unencrypted OpenSSH private key. A passphrase-protected key gives
     * [SshError.EncryptedKey]; anything damaged gives [SshError.CorruptKey]; other key types
     * (ECDSA, certificates) give [SshError.UnsupportedKeyFormat].
     */
    fun parsePrivate(text: String): SshResult<SshPrivateKey> {
        val body = text.trim().removePrefix(BEGIN).removeSuffix(END)
        val framed = text.trim().startsWith(BEGIN) && text.trim().endsWith(END)
        return if (!framed) {
            SshResult.Failure(SshError.UnsupportedKeyFormat)
        } else {
            OpenSshKeyReader.decode(body)
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
