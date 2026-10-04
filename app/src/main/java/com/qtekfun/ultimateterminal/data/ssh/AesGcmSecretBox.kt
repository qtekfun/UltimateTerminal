// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.ssh

import com.qtekfun.ultimateterminal.domain.ssh.SecretBox
import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals secrets with AES-256-GCM under a key that [keyProvider] returns. On the device the key
 * lives in the Android Keystore and never leaves it; the tests use an in-memory key, so the format
 * and the integrity checks are tested for real.
 *
 * Sealed layout: `version (1 byte) | IV length (1 byte) | IV | ciphertext + tag`. The `context` is
 * authenticated data: it is not stored, and a blob opened with another one fails, so a key blob
 * cannot be moved to another alias.
 */
class AesGcmSecretBox(private val keyProvider: () -> SecretKey) : SecretBox {
    override fun seal(plain: ByteArray, context: String): SshResult<ByteArray> = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV is passed: the provider picks a fresh random one, as the Keystore requires.
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val sealed = byteArrayOf(VERSION, iv.size.toByte()) + iv + cipher.doFinal(plain)
        SshResult.Success(sealed)
    } catch (_: GeneralSecurityException) {
        SshResult.Failure(SshError.Storage("cannot seal with the key store"))
    }

    override fun open(sealed: ByteArray, context: String): SshResult<ByteArray> {
        val ivSize = sealed.getOrNull(1)?.toInt() ?: -1
        val valid =
            sealed.size > HEADER_BYTES + ivSize && sealed[0] == VERSION && ivSize == IV_BYTES
        return if (!valid) {
            SshResult.Failure(SshError.CorruptKey)
        } else {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val iv = sealed.copyOfRange(HEADER_BYTES, HEADER_BYTES + ivSize)
                cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
                SshResult.Success(
                    cipher.doFinal(
                        sealed,
                        HEADER_BYTES + ivSize,
                        sealed.size - HEADER_BYTES - ivSize
                    )
                )
            } catch (_: GeneralSecurityException) {
                // A wrong key, another context or tampered bytes all end here, with no detail.
                SshResult.Failure(SshError.CorruptKey)
            }
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val HEADER_BYTES = 2
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
