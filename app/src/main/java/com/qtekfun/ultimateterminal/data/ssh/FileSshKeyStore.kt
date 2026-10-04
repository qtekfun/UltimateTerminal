// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.ssh

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.SecretFileStore
import com.qtekfun.ultimateterminal.domain.ssh.SecretBox
import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.domain.ssh.TokenSource
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keeps keys as two files per alias under `ssh-keys/`: `<alias>.key` holds the private key sealed
 * by the [SecretBox], `<alias>.meta` holds what the list shows (name, type, public key, fingerprint).
 * The `.meta` is written last, so a key whose write was interrupted never appears in the list.
 */
class FileSshKeyStore(
    private val files: SecretFileStore,
    private val box: SecretBox,
    private val tokens: TokenSource
) : SshKeyStore {
    private val changes = MutableStateFlow(0)

    override fun observe(): Flow<List<SshKeyInfo>> = changes.map { load() }

    override suspend fun put(info: SshKeyInfo, privateKey: String): SshResult<Unit> {
        val key = path(info.alias, KEY_SUFFIX)
        val meta = path(info.alias, META_SUFFIX)
        val sealed = box.seal(privateKey.toByteArray(Charsets.UTF_8), info.alias)
        val result = when {
            key == null || meta == null -> SshResult.Failure(SshError.Invalid("alias"))

            sealed is SshResult.Failure -> sealed

            sealed is SshResult.Success -> {
                val json = Json.encodeToString(KeyMeta.of(info)).toByteArray(Charsets.UTF_8)
                storage(files.write(key, sealed.value, ownerOnly = true)) {
                    storage(files.write(meta, json, ownerOnly = true)) { SshResult.Success(Unit) }
                }
            }

            else -> SshResult.Failure(SshError.CorruptKey)
        }
        changes.value++
        return result
    }

    override suspend fun privateKey(alias: String): SshResult<String> {
        val key = path(alias, KEY_SUFFIX)
        val stored = key?.let { files.read(it) }
        return when {
            key == null || stored !is Outcome.Success -> SshResult.Failure(SshError.KeyNotFound)

            else -> when (val opened = box.open(stored.value, alias)) {
                is SshResult.Success -> SshResult.Success(String(opened.value, Charsets.UTF_8))
                is SshResult.Failure -> opened
            }
        }
    }

    override suspend fun delete(alias: String): SshResult<Unit> {
        val key = path(alias, KEY_SUFFIX)
        val meta = path(alias, META_SUFFIX)
        val result = if (key == null || meta == null) {
            SshResult.Failure(SshError.Invalid("alias"))
        } else {
            // The list entry goes first, so a failure half way leaves an invisible blob, not a
            // listed key that cannot be used.
            storage(files.delete(meta)) { storage(files.delete(key)) { SshResult.Success(Unit) } }
        }
        changes.value++
        return result
    }

    override fun newAlias(): String = "k${tokens.hex(ALIAS_BYTES)}"

    private suspend fun load(): List<SshKeyInfo> {
        val names = files.listNames(DIRECTORY).getOrNull().orEmpty()
        return names.filter { it.endsWith(META_SUFFIX) }.mapNotNull { name ->
            // The alias is the file name: a list entry cannot claim to be another key.
            val alias = name.removeSuffix(META_SUFFIX)
            val bytes = path(alias, META_SUFFIX)?.let { files.read(it).getOrNull() }
            bytes?.let(::decode)?.toInfo(alias)
        }.sortedBy { it.name.lowercase() }
    }

    private fun decode(bytes: ByteArray): KeyMeta? =
        runCatching { Json.decodeFromString<KeyMeta>(String(bytes, Charsets.UTF_8)) }.getOrNull()

    // An alias comes from the store itself, but one read from a file is checked before it is part
    // of a path, so a damaged or hostile `.meta` cannot name another file.
    private fun path(alias: String, suffix: String): FsPath? =
        if (aliasPattern.matches(alias)) DIRECTORY.child("$alias$suffix").getOrNull() else null

    private inline fun <T> storage(outcome: Outcome<Unit>, next: () -> SshResult<T>): SshResult<T> =
        when (outcome) {
            is Outcome.Success -> next()
            is Outcome.Failure -> SshResult.Failure(SshError.Storage("cannot write the key store"))
        }

    @Serializable
    private class KeyMeta(
        val version: Int,
        val name: String,
        val type: String,
        val publicKey: String,
        val fingerprint: String,
        val createdAt: String
    ) {
        fun toInfo(alias: String): SshKeyInfo? {
            val keyType = SshKeyType.entries.firstOrNull { it.name == type }
            val time = runCatching { Instant.parse(createdAt) }.getOrNull()
            return if (keyType == null || time == null || version != VERSION) {
                null
            } else {
                SshKeyInfo(alias, name, keyType, publicKey, fingerprint, time)
            }
        }

        companion object {
            fun of(info: SshKeyInfo) = KeyMeta(
                VERSION,
                info.name,
                info.type.name,
                info.publicKey,
                info.fingerprint,
                info.createdAt.toString()
            )
        }
    }

    private companion object {
        const val VERSION = 1
        const val KEY_SUFFIX = ".key"
        const val META_SUFFIX = ".meta"
        const val ALIAS_BYTES = 6
        val aliasPattern = Regex("k[0-9a-f]{12}")
        val DIRECTORY: FsPath = requireNotNull(FsPath.of("ssh-keys").getOrNull())
    }
}
