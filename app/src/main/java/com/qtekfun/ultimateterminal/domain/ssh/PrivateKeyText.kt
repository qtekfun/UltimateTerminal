// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Reads a private key a user pasted or picked, whatever the file format: OpenSSH (Ed25519, RSA),
 * PKCS#8 and PKCS#1 (RSA). A passphrase-protected key is refused with [SshError.EncryptedKey].
 *
 * Ed25519 in PKCS#8 is not supported: the public key has to be computed from the secret, which the
 * platform's cryptography API does not offer.
 */
object PrivateKeyText {
    private const val OPENSSH = "OPENSSH PRIVATE KEY"
    private const val PKCS8 = "PRIVATE KEY"
    private const val PKCS1_RSA = "RSA PRIVATE KEY"
    private const val ENCRYPTED_PKCS8 = "ENCRYPTED PRIVATE KEY"
    private const val RSA_FIELDS = 9
    private val header = Regex("""-----BEGIN ([A-Z0-9 ]+)-----""")

    fun parse(text: String): SshResult<SshPrivateKey> {
        val kind = header.find(text.trim())?.groupValues?.get(1)
        val body = text.trim().lines().filterNot {
            it.startsWith("-----") || ":" in it
        }.joinToString("")
        return when {
            kind == OPENSSH -> OpenSshKeyFormat.parsePrivate(text)

            kind == ENCRYPTED_PKCS8 || "ENCRYPTED" in text.take(ENCRYPTED_SCAN) ->
                SshResult.Failure(SshError.EncryptedKey)

            kind == PKCS8 -> rsaFromPkcs8(body)

            kind == PKCS1_RSA -> rsaFromPkcs1(body)

            else -> SshResult.Failure(SshError.UnsupportedKeyFormat)
        }
    }

    private fun rsaFromPkcs8(base64: String): SshResult<SshPrivateKey> {
        val der = runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
        val key = der?.let {
            runCatching {
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(it))
            }.getOrNull()
        } as? RSAPrivateCrtKey
        return if (key == null) {
            SshResult.Failure(SshError.UnsupportedKeyFormat)
        } else {
            SshResult.Success(
                SshPrivateKey.Rsa(
                    key.modulus,
                    key.publicExponent,
                    key.privateExponent,
                    key.primeP,
                    key.primeQ,
                    key.crtCoefficient
                )
            )
        }
    }

    private fun rsaFromPkcs1(base64: String): SshResult<SshPrivateKey> {
        val der = runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
        val numbers = der?.let(DerSequence::integersOf)
        return if (numbers == null || numbers.size < RSA_FIELDS || numbers[0] != BigInteger.ZERO) {
            SshResult.Failure(SshError.CorruptKey)
        } else {
            // version, n, e, d, p, q, dp, dq, qinv: the exponents dp and dq are recomputed by ssh
            SshResult.Success(
                SshPrivateKey.Rsa(
                    n = numbers[1],
                    e = numbers[2],
                    d = numbers[3],
                    p = numbers[4],
                    q = numbers[5],
                    iqmp = numbers[RSA_FIELDS - 1]
                )
            )
        }
    }

    private const val ENCRYPTED_SCAN = 200
}
