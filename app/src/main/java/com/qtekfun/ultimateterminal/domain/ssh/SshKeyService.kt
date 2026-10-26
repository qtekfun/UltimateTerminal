// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Validation
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Where keys are kept. The implementation seals the private part; callers see it only as the
 * OpenSSH text.
 */
interface SshKeyStore {
    fun observe(): Flow<List<SshKeyInfo>>

    suspend fun put(info: SshKeyInfo, privateKey: String): SshResult<Unit>

    suspend fun privateKey(alias: String): SshResult<String>

    suspend fun delete(alias: String): SshResult<Unit>

    /** A fresh alias nobody has used. */
    fun newAlias(): String
}

/** Generates, imports, exports and removes keys. Rules live here; storage is the [SshKeyStore]. */
class SshKeyService(
    private val store: SshKeyStore,
    private val generator: SshKeyGenerator,
    private val hosts: SshHostRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val unlocks: PassphraseCache = PassphraseCache()
) {
    val supportedTypes: List<SshKeyType> get() = generator.supportedTypes

    fun observe(): Flow<List<SshKeyInfo>> = store.observe()

    /** Makes a key; with a [passphrase] the stored private key is encrypted with it. */
    suspend fun generate(
        name: String,
        type: SshKeyType,
        passphrase: CharArray? = null
    ): SshResult<SshKeyInfo> = withPassphrase(passphrase, checkLength = true) { bytes ->
        withContext(cpuDispatcher) { generator.generate(type) }
            .flatMap { key -> save(name, key, bytes) }
    }

    /**
     * Imports a private key given as text (OpenSSH, PKCS#8 or PKCS#1 RSA). An OpenSSH key that is
     * protected needs its [passphrase] and stays protected by it. A key that is not protected
     * ignores [passphrase]; protect it afterwards with [changePassphrase].
     */
    suspend fun import(
        name: String,
        text: String,
        passphrase: CharArray? = null
    ): SshResult<SshKeyInfo> = withPassphrase(passphrase, checkLength = false) { bytes ->
        withContext(cpuDispatcher) { PrivateKeyText.parse(text, bytes) }.flatMap { key ->
            save(name, key, bytes.takeIf { OpenSshKeyFormat.isProtected(text) })
        }
    }

    /**
     * Sets, changes or removes the passphrase of a stored key. [current] opens a protected key
     * (it is ignored for an unprotected one); a null or empty [replacement] leaves the key
     * unprotected. Whatever was remembered for the key is forgotten.
     */
    suspend fun changePassphrase(
        alias: String,
        current: CharArray?,
        replacement: CharArray?
    ): SshResult<SshKeyInfo> {
        val info = store.observe().first().firstOrNull { it.alias == alias }
        return if (info == null) {
            SshResult.Failure(SshError.KeyNotFound)
        } else {
            withPassphrase(current, checkLength = false) { old ->
                withPassphrase(replacement, checkLength = true) { new ->
                    store.privateKey(alias).flatMap { text -> rewrap(info, text, old, new) }
                }
            }
        }
    }

    private suspend fun rewrap(
        info: SshKeyInfo,
        text: String,
        old: ByteArray?,
        new: ByteArray?
    ): SshResult<SshKeyInfo> {
        val encoded = withContext(cpuDispatcher) {
            OpenSshKeyFormat.parsePrivate(text, old).map { key ->
                OpenSshKeyFormat.encodePrivate(key, info.name, new)
            }
        }
        val updated = info.copy(passphraseProtected = new != null)
        return encoded.flatMap { store.put(updated, it) }.map { updated }
            .also { unlocks.forget(info.alias) }
    }

    // Hands [block] the passphrase as bytes (null if there is none) and wipes them afterwards. With
    // [checkLength], a passphrase shorter than the minimum is refused before anything is done.
    private suspend fun <T> withPassphrase(
        chars: CharArray?,
        checkLength: Boolean,
        block: suspend (ByteArray?) -> SshResult<T>
    ): SshResult<T> {
        val given = chars?.takeIf { it.isNotEmpty() }
        if (checkLength && given != null && given.size < Passphrases.MIN_LENGTH) {
            return SshResult.Failure(SshError.PassphraseTooShort(Passphrases.MIN_LENGTH))
        }
        val bytes = given?.let(Passphrases::toBytes)
        return try {
            block(bytes)
        } finally {
            bytes?.fill(0)
        }
    }

    /** The private key as OpenSSH text. The UI must warn before showing or saving it. */
    suspend fun exportPrivate(alias: String): SshResult<String> = store.privateKey(alias)

    suspend fun exportPublic(alias: String): SshResult<String> {
        val info = store.observe().first().firstOrNull { it.alias == alias }
        return if (info == null) {
            SshResult.Failure(SshError.KeyNotFound)
        } else {
            SshResult.Success(info.publicKey)
        }
    }

    /** Removes a key unless a saved host still uses it. */
    suspend fun remove(alias: String): SshResult<Unit> {
        val users = hosts.observeAll().first().count { it.keyAlias == alias }
        return if (users > 0) {
            SshResult.Failure(SshError.KeyInUse(users))
        } else {
            store.delete(alias).also { unlocks.forget(alias) }
        }
    }

    private suspend fun save(
        rawName: String,
        key: SshPrivateKey,
        passphrase: ByteArray?
    ): SshResult<SshKeyInfo> {
        val name = (Validation.name(rawName) as? Outcome.Success)?.value
        val taken = name != null && store.observe().first().any { it.name.equals(name, true) }
        return when {
            name == null -> SshResult.Failure(SshError.Invalid("name"))

            taken -> SshResult.Failure(SshError.NameTaken(name))

            else -> {
                val info = SshKeyInfo(
                    alias = store.newAlias(),
                    name = name,
                    type = key.type,
                    publicKey = OpenSshKeyFormat.publicLine(key, name),
                    fingerprint = OpenSshKeyFormat.fingerprint(key),
                    createdAt = Instant.now(clock),
                    passphraseProtected = passphrase != null
                )
                val text = withContext(cpuDispatcher) {
                    OpenSshKeyFormat.encodePrivate(key, name, passphrase)
                }
                store.put(info, text).map { info }
            }
        }
    }
}
