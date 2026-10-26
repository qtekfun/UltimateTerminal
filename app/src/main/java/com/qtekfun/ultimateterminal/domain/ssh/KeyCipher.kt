// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A cipher `openssh-key-v1` can protect a private key with: AES in CTR or CBC mode, keyed by
 * [BcryptPbkdf]. The key and the IV both come out of the same derivation, in that order. The
 * AEAD ciphers OpenSSH also knows (`aes*-gcm@openssh.com`, `chacha20-poly1305@openssh.com`) are not
 * read; `ssh-keygen` does not choose them unless asked to.
 */
internal class KeyCipher private constructor(
    private val keyLength: Int,
    private val transformation: String
) {
    val blockSize: Int = AES_BLOCK

    fun decrypt(passphrase: ByteArray, salt: ByteArray, rounds: Int, data: ByteArray): ByteArray =
        run(Cipher.DECRYPT_MODE, passphrase, salt, rounds, data)

    fun encrypt(passphrase: ByteArray, salt: ByteArray, rounds: Int, data: ByteArray): ByteArray =
        run(Cipher.ENCRYPT_MODE, passphrase, salt, rounds, data)

    private fun run(
        mode: Int,
        passphrase: ByteArray,
        salt: ByteArray,
        rounds: Int,
        data: ByteArray
    ): ByteArray {
        val material = BcryptPbkdf.derive(passphrase, salt, rounds, keyLength + AES_BLOCK)
        try {
            val key = SecretKeySpec(material.copyOfRange(0, keyLength), "AES")
            val iv = IvParameterSpec(material.copyOfRange(keyLength, keyLength + AES_BLOCK))
            val cipher = Cipher.getInstance(transformation)
            cipher.init(mode, key, iv)
            return cipher.doFinal(data)
        } catch (_: GeneralSecurityException) {
            throw MalformedKey()
        } finally {
            material.fill(0)
        }
    }

    companion object {
        private const val AES_BLOCK = 16
        private const val CTR = "AES/CTR/NoPadding"
        private const val CBC = "AES/CBC/NoPadding"
        private const val KEY_128 = 16
        private const val KEY_192 = 24
        private const val KEY_256 = 32

        /** What the app writes: what `ssh-keygen` uses by default. */
        const val DEFAULT_NAME = "aes256-ctr"

        private val known = mapOf(
            "aes128-ctr" to KeyCipher(KEY_128, CTR),
            "aes192-ctr" to KeyCipher(KEY_192, CTR),
            DEFAULT_NAME to KeyCipher(KEY_256, CTR),
            "aes128-cbc" to KeyCipher(KEY_128, CBC),
            "aes192-cbc" to KeyCipher(KEY_192, CBC),
            "aes256-cbc" to KeyCipher(KEY_256, CBC)
        )

        fun named(name: String): KeyCipher? = known[name]

        val default: KeyCipher get() = requireNotNull(known[DEFAULT_NAME])
    }
}
