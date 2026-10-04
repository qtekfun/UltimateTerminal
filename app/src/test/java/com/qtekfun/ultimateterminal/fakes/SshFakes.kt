// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.fakes

import com.qtekfun.ultimateterminal.data.ssh.AesGcmSecretBox
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.repository.SecretFileStore
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyGenerator
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshPrivateKey
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.domain.ssh.TokenSource
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [SecretFileStore] that remembers which files were written owner-only. */
class InMemorySecretFileStore : SecretFileStore {
    val files = linkedMapOf<String, ByteArray>()
    val ownerOnly = mutableMapOf<String, Boolean>()
    var failWrites = false

    override suspend fun write(path: FsPath, bytes: ByteArray, ownerOnly: Boolean): Outcome<Unit> =
        if (failWrites) {
            Outcome.Failure(DomainError.Io("disk full"))
        } else {
            files[path.value] = bytes.copyOf()
            this.ownerOnly[path.value] = ownerOnly
            Outcome.Success(Unit)
        }

    override suspend fun read(path: FsPath): Outcome<ByteArray> =
        files[path.value]?.let { Outcome.Success(it.copyOf()) }
            ?: Outcome.Failure(DomainError.NotFound)

    override suspend fun listNames(directory: FsPath): Outcome<List<String>> {
        val prefix = directory.value + "/"
        return Outcome.Success(
            files.keys.filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
                .map { it.removePrefix(prefix) }
                .sorted()
        )
    }

    override suspend fun delete(path: FsPath): Outcome<Unit> {
        files.remove(path.value)
        ownerOnly.remove(path.value)
        return Outcome.Success(Unit)
    }
}

class FakeSshHostRepository : SshHostRepository {
    private val hosts = MutableStateFlow<List<SshHost>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<SshHost>> = hosts.map { it.sortedBy { host -> host.name } }

    override suspend fun get(id: Long): SshHost? = hosts.value.firstOrNull { it.id == id }

    override suspend fun add(host: SshHost): Outcome<SshHost> {
        val saved = host.copy(id = nextId++)
        hosts.value = hosts.value + saved
        return Outcome.Success(saved)
    }

    override suspend fun update(host: SshHost): Outcome<Unit> {
        hosts.value = hosts.value.map { if (it.id == host.id) host else it }
        return Outcome.Success(Unit)
    }

    override suspend fun remove(id: Long): Outcome<Unit> {
        hosts.value = hosts.value.filterNot { it.id == id }
        return Outcome.Success(Unit)
    }
}

/** Keeps keys in memory; the private text is stored as given. */
class FakeSshKeyStore : SshKeyStore {
    private val infos = MutableStateFlow<List<SshKeyInfo>>(emptyList())
    private val secrets = mutableMapOf<String, String>()
    private var counter = 0
    var failPuts = false

    override fun observe(): Flow<List<SshKeyInfo>> = infos

    override suspend fun put(info: SshKeyInfo, privateKey: String): SshResult<Unit> =
        if (failPuts) {
            SshResult.Failure(SshError.Storage("disk full"))
        } else {
            infos.value = infos.value + info
            secrets[info.alias] = privateKey
            SshResult.Success(Unit)
        }

    override suspend fun privateKey(alias: String): SshResult<String> =
        secrets[alias]?.let { SshResult.Success(it) } ?: SshResult.Failure(SshError.KeyNotFound)

    override suspend fun delete(alias: String): SshResult<Unit> {
        infos.value = infos.value.filterNot { it.alias == alias }
        secrets.remove(alias)
        return SshResult.Success(Unit)
    }

    override fun newAlias(): String = "k%012x".format(++counter)
}

/** A generator that returns a fixed key, so service tests do not wait for real key generation. */
class FixedKeyGenerator(
    private val key: SshPrivateKey,
    override val supportedTypes: List<SshKeyType> = SshKeyType.entries
) : SshKeyGenerator {
    override fun generate(type: SshKeyType): SshResult<SshPrivateKey> =
        if (type in supportedTypes) {
            SshResult.Success(key)
        } else {
            SshResult.Failure(SshError.UnsupportedKeyType(type))
        }
}

/** Predictable tokens: 01, 02, ... as hexadecimal text of the requested length. */
class CountingTokens : TokenSource {
    private var counter = 0

    override fun hex(byteCount: Int): String = (++counter).toString(16).padStart(byteCount * 2, '0')
}

fun testSecretBox(keyByte: Int = 7): AesGcmSecretBox =
    AesGcmSecretBox { SecretKeySpec(ByteArray(32) { keyByte.toByte() }, "AES") }
