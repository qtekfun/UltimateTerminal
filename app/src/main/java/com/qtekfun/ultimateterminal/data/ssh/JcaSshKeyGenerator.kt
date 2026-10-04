// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.ssh

import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyGenerator
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshPrivateKey
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey

/**
 * Generates keys with the platform's own cryptography (`java.security`), so no library is added.
 * RSA works everywhere; Ed25519 only where the platform has it (Android 13 and later, any recent JDK).
 */
class JcaSshKeyGenerator(private val rsaBits: Int = RSA_BITS) : SshKeyGenerator {
    override val supportedTypes: List<SshKeyType> = SshKeyType.entries.filter(::available)

    override fun generate(type: SshKeyType): SshResult<SshPrivateKey> = try {
        when (type) {
            SshKeyType.ED25519 -> ed25519()
            SshKeyType.RSA -> rsa()
        }
    } catch (_: GeneralSecurityException) {
        SshResult.Failure(SshError.UnsupportedKeyType(type))
    }

    private fun available(type: SshKeyType): Boolean = try {
        KeyPairGenerator.getInstance(algorithm(type))
        true
    } catch (_: GeneralSecurityException) {
        false
    }

    private fun algorithm(type: SshKeyType): String = when (type) {
        SshKeyType.ED25519 -> "Ed25519"
        SshKeyType.RSA -> "RSA"
    }

    // The platform encodes Ed25519 keys as PKCS#8 (a 16-byte header, then the 32-byte seed) and
    // X.509 (a 12-byte header, then the 32-byte public key): the raw bytes are the tail of each.
    private fun ed25519(): SshResult<SshPrivateKey> {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val secret = pair.private.encoded
        val publicKey = pair.public.encoded
        return if (secret.size == PKCS8_ED25519 && publicKey.size == X509_ED25519) {
            SshResult.Success(
                SshPrivateKey.Ed25519(
                    secret.copyOfRange(secret.size - KEY_BYTES, secret.size),
                    publicKey.copyOfRange(publicKey.size - KEY_BYTES, publicKey.size)
                )
            )
        } else {
            SshResult.Failure(SshError.UnsupportedKeyType(SshKeyType.ED25519))
        }
    }

    private fun rsa(): SshResult<SshPrivateKey> {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(rsaBits) }
        val key = generator.generateKeyPair().private as? RSAPrivateCrtKey
        return if (key == null) {
            SshResult.Failure(SshError.UnsupportedKeyType(SshKeyType.RSA))
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

    private companion object {
        const val RSA_BITS = 3072
        const val KEY_BYTES = 32
        const val PKCS8_ED25519 = 48
        const val X509_ED25519 = 44
    }
}
