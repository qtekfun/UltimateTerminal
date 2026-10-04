// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The AES-256 key, held by the Android Keystore, that seals the app's private keys. The Keystore
 * never gives the key material out: the app can only ask it to encrypt and decrypt. It needs a
 * device to run, so it is not covered by unit tests; the sealing format around it is.
 */
object KeystoreSecretKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "ultimateterminal-ssh-seal-v1"
    private const val KEY_BITS = 256

    /** Returns the key, creating it the first time. */
    @Synchronized
    fun get(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey) ?: create()
    }

    private fun create(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}
