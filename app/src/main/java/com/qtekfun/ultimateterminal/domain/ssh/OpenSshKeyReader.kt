// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.util.Base64

/** Reads the body of an `openssh-key-v1` file. See [OpenSshKeyFormat.parsePrivate]. */
internal object OpenSshKeyReader {
    private const val NONE = "none"
    private const val ED25519_KEY_BYTES = 32
    private const val ED25519_PAIR_BYTES = 64

    fun decode(body: String): SshResult<SshPrivateKey> {
        val raw = runCatching { Base64.getMimeDecoder().decode(body.filterNot(Char::isWhitespace)) }
            .getOrNull()
        return if (raw == null) {
            SshResult.Failure(SshError.CorruptKey)
        } else {
            try {
                readFile(SshReader(raw))
            } catch (_: MalformedKey) {
                SshResult.Failure(SshError.CorruptKey)
            }
        }
    }

    private fun readFile(reader: SshReader): SshResult<SshPrivateKey> {
        reader.expect(OpenSshKeyFormat.MAGIC)
        val cipher = reader.text()
        val kdf = reader.text()
        reader.bytes()
        val count = reader.int()
        return when {
            cipher != NONE || kdf != NONE -> SshResult.Failure(SshError.EncryptedKey)

            count != 1 -> SshResult.Failure(SshError.UnsupportedKeyFormat)

            else -> {
                reader.bytes()
                readSection(SshReader(reader.bytes()))
            }
        }
    }

    private fun readSection(section: SshReader): SshResult<SshPrivateKey> {
        val first = section.int()
        val second = section.int()
        if (first != second) throw MalformedKey()
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
