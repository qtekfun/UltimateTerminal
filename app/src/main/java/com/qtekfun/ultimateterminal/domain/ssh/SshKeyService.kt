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
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    val supportedTypes: List<SshKeyType> get() = generator.supportedTypes

    fun observe(): Flow<List<SshKeyInfo>> = store.observe()

    suspend fun generate(name: String, type: SshKeyType): SshResult<SshKeyInfo> =
        withContext(cpuDispatcher) { generator.generate(type) }.flatMap { key -> save(name, key) }

    /** Imports an unencrypted private key given as text (OpenSSH, PKCS#8 or PKCS#1 RSA). */
    suspend fun import(name: String, text: String): SshResult<SshKeyInfo> =
        PrivateKeyText.parse(text).flatMap { key -> save(name, key) }

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
            store.delete(alias)
        }
    }

    private suspend fun save(rawName: String, key: SshPrivateKey): SshResult<SshKeyInfo> {
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
                    createdAt = Instant.now(clock)
                )
                store.put(info, OpenSshKeyFormat.encodePrivate(key, name)).map { info }
            }
        }
    }
}
