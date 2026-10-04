// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.math.BigInteger
import java.time.Instant

enum class SshKeyType(val sshName: String) {
    ED25519("ssh-ed25519"),
    RSA("ssh-rsa")
}

/** What the app shows about a stored key. The private part never appears here. */
data class SshKeyInfo(
    /** Stable identifier; it is what a host stores in `SshHost.keyAlias`. */
    val alias: String,
    val name: String,
    val type: SshKeyType,
    /** The `authorized_keys` line, safe to show and copy. */
    val publicKey: String,
    /** `SHA256:...`, as `ssh-keygen -l` prints it. */
    val fingerprint: String,
    val createdAt: Instant
)

/**
 * A private key in memory. These are plain classes, not data classes, on purpose: a data class
 * prints its fields in `toString`, and a key must never end up in a log by accident.
 */
sealed interface SshPrivateKey {
    val type: SshKeyType

    /** [seed] is the 32-byte Ed25519 secret; [publicKey] is the matching 32-byte public key. */
    class Ed25519(val seed: ByteArray, val publicKey: ByteArray) : SshPrivateKey {
        override val type: SshKeyType = SshKeyType.ED25519
    }

    /** An RSA key with its CRT parameters, the form OpenSSH stores ([iqmp] is q^-1 mod p). */
    class Rsa(
        val n: BigInteger,
        val e: BigInteger,
        val d: BigInteger,
        val p: BigInteger,
        val q: BigInteger,
        val iqmp: BigInteger
    ) : SshPrivateKey {
        override val type: SshKeyType = SshKeyType.RSA
    }
}

/** Creates keys. The real implementation uses the platform's cryptography. */
interface SshKeyGenerator {
    /** The types this device can generate; Ed25519 needs Android 13 or later. */
    val supportedTypes: List<SshKeyType>

    fun generate(type: SshKeyType): SshResult<SshPrivateKey>
}

/**
 * Seals small secrets for storage at rest. [context] is bound to the sealed bytes, so a blob
 * cannot be swapped for another key's blob without failing to open.
 */
interface SecretBox {
    fun seal(plain: ByteArray, context: String): SshResult<ByteArray>

    fun open(sealed: ByteArray, context: String): SshResult<ByteArray>
}
