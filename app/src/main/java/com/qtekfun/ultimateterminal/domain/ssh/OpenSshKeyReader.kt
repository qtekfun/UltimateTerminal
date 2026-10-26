// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.util.Base64

/** Reads the body of an `openssh-key-v1` file. See [OpenSshKeyFormat.parsePrivate]. */
internal object OpenSshKeyReader {
    private const val NONE = "none"
    private const val BCRYPT = "bcrypt"
    private const val ED25519_KEY_BYTES = 32
    private const val ED25519_PAIR_BYTES = 64
    private const val MAX_ROUNDS = 1024

    /** The cipher named in the header: `none` for an unprotected key, null if it does not parse. */
    fun cipherName(body: String): String? {
        val raw = runCatching { Base64.getMimeDecoder().decode(body.filterNot(Char::isWhitespace)) }
            .getOrNull()
        return try {
            raw?.let {
                SshReader(it).also { reader -> reader.expect(OpenSshKeyFormat.MAGIC) }.text()
            }
        } catch (_: MalformedKey) {
            null
        }
    }

    fun decode(body: String, passphrase: ByteArray?): SshResult<SshPrivateKey> {
        val raw = runCatching { Base64.getMimeDecoder().decode(body.filterNot(Char::isWhitespace)) }
            .getOrNull()
        return if (raw == null) {
            SshResult.Failure(SshError.CorruptKey)
        } else {
            try {
                readFile(SshReader(raw), passphrase)
            } catch (_: MalformedKey) {
                SshResult.Failure(SshError.CorruptKey)
            }
        }
    }

    private fun readFile(reader: SshReader, passphrase: ByteArray?): SshResult<SshPrivateKey> {
        reader.expect(OpenSshKeyFormat.MAGIC)
        val cipherName = reader.text()
        val kdf = reader.text()
        val kdfOptions = reader.bytes()
        val count = reader.int()
        return when {
            count != 1 -> SshResult.Failure(SshError.UnsupportedKeyFormat)

            cipherName == NONE && kdf == NONE -> {
                reader.bytes()
                readSection(SshReader(reader.bytes()), protected = false)
            }

            cipherName == NONE || kdf != BCRYPT -> SshResult.Failure(SshError.UnsupportedKeyFormat)

            else -> {
                reader.bytes()
                val sealed = reader.bytes()
                decryptSection(cipherName, kdfOptions, sealed, passphrase)
            }
        }
    }

    private fun decryptSection(
        cipherName: String,
        kdfOptions: ByteArray,
        sealed: ByteArray,
        passphrase: ByteArray?
    ): SshResult<SshPrivateKey> {
        val cipher = KeyCipher.named(cipherName)
        val options = SshReader(kdfOptions)
        val salt = options.bytes()
        val rounds = options.int()
        val usable = cipher != null && salt.isNotEmpty() && rounds in 1..MAX_ROUNDS
        return when {
            !usable -> SshResult.Failure(SshError.UnsupportedKeyFormat)

            passphrase == null || passphrase.isEmpty() ->
                SshResult.Failure(SshError.PassphraseRequired)

            sealed.isEmpty() || sealed.size % cipher.blockSize != 0 -> throw MalformedKey()

            else -> {
                val plain = cipher.decrypt(passphrase, salt, rounds, sealed)
                try {
                    readSection(SshReader(plain), protected = true)
                } finally {
                    plain.fill(0)
                }
            }
        }
    }

    // In a protected key, two check words that differ mean the passphrase was wrong.
    private fun readSection(section: SshReader, protected: Boolean): SshResult<SshPrivateKey> {
        val first = section.int()
        val second = section.int()
        if (first != second) {
            if (protected) return SshResult.Failure(SshError.WrongPassphrase)
            throw MalformedKey()
        }
        val key = when (section.text()) {
            SshKeyType.ED25519.sshName -> readEd25519(section)
            SshKeyType.RSA.sshName -> readRsa(section)
            else -> null
        }
        return if (key == null) {
            SshResult.Failure(SshError.UnsupportedKeyFormat)
        } else {
            SshResult.Success(key)
        }
    }

    private fun readEd25519(section: SshReader): SshPrivateKey {
        val publicKey = section.bytes()
        val pair = section.bytes()
        val valid = publicKey.size == ED25519_KEY_BYTES &&
            pair.size == ED25519_PAIR_BYTES &&
            pair.copyOfRange(ED25519_KEY_BYTES, ED25519_PAIR_BYTES).contentEquals(publicKey)
        if (!valid) throw MalformedKey()
        return SshPrivateKey.Ed25519(pair.copyOfRange(0, ED25519_KEY_BYTES), publicKey)
    }

    private fun readRsa(section: SshReader): SshPrivateKey {
        val n = section.mpint()
        val e = section.mpint()
        val d = section.mpint()
        val iqmp = section.mpint()
        val p = section.mpint()
        val q = section.mpint()
        if (p * q != n) throw MalformedKey()
        return SshPrivateKey.Rsa(n, e, d, p, q, iqmp)
    }
}
