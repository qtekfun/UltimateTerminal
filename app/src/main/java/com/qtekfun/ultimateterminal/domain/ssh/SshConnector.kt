// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.SecretFileStore
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository

/** Random hexadecimal text; faked in tests so file names are predictable. */
fun interface TokenSource {
    fun hex(byteCount: Int): String
}

/**
 * Everything needed to open the connection in a tab: the distro to run in and the command inside
 * it. [cleanup] removes the key file the plan materialized; call it when the session ends.
 */
class SshLaunchPlan(
    val distro: Distro,
    val guestCommand: List<String>,
    val cleanup: suspend () -> Unit
) {
    override fun toString(): String = "SshLaunchPlan(distro=${distro.id})"
}

/**
 * Prepares a one-touch connection to a saved host.
 *
 * The private key is decrypted only here and written to a file inside the distro's `/tmp`, readable
 * by the app alone, because `ssh -i` needs a file. The file lives as long as the session and is
 * removed when it ends; any left behind by a crash is removed the next time a connection starts.
 * DECISIONS.md (D-T14) says what this does and does not protect against.
 */
class SshConnector(
    private val hosts: SshHostRepository,
    private val distros: DistroRepository,
    private val keys: SshKeyStore,
    private val files: SecretFileStore,
    private val tokens: TokenSource
) {
    private val live = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun prepare(hostId: Long): SshResult<SshLaunchPlan> {
        val host = hosts.get(hostId)
        val distro = host?.let { chooseDistro(it) }
        return when {
            host == null -> SshResult.Failure(SshError.HostNotFound)
            distro == null -> SshResult.Failure(SshError.NoDistro)
            else -> plan(host, distro)
        }
    }

    private suspend fun chooseDistro(host: SshHost): Distro? {
        val wanted = host.distroId?.let { distros.get(it) } ?: distros.getDefault()
        return wanted?.takeIf { it.state == DistroState.READY }
    }

    private suspend fun plan(host: SshHost, distro: Distro): SshResult<SshLaunchPlan> {
        val tmp = distro.directory.child(TMP).getOrNull()
        removeStaleKeys(tmp)
        ensureResolver(distro)
        val alias = host.keyAlias
        val materialized = if (alias == null) {
            SshResult.Success(null)
        } else if (tmp == null) {
            SshResult.Failure(SshError.Storage("no temporary directory"))
        } else {
            materialize(alias, tmp)
        }
        return materialized.flatMap { key ->
            val guestPath = key?.let { "/$TMP/${it.name}" }
            when (val command = SshCommand.build(host, guestPath)) {
                is SshResult.Failure -> {
                    key?.let { release(it) }
                    command
                }

                is SshResult.Success -> SshResult.Success(
                    SshLaunchPlan(distro, command.value) { key?.let { release(it) } }
                )
            }
        }
    }

    private suspend fun materialize(alias: String, tmp: FsPath): SshResult<KeyFile> {
        val name = "$KEY_PREFIX${tokens.hex(TOKEN_BYTES)}"
        val path = tmp.child(name).getOrNull()
        val text = keys.privateKey(alias)
        return when {
            path == null -> SshResult.Failure(SshError.Invalid("keyPath"))

            text is SshResult.Failure -> text

            text is SshResult.Success -> {
                val bytes = text.value.toByteArray(Charsets.UTF_8)
                when (files.write(path, bytes, ownerOnly = true)) {
                    is Outcome.Failure -> SshResult.Failure(
                        SshError.Storage("cannot write the key file")
                    )

                    is Outcome.Success -> {
                        live += name
                        SshResult.Success(KeyFile(name, path))
                    }
                }.also { bytes.fill(0) }
            }

            else -> SshResult.Failure(SshError.KeyNotFound)
        }
    }

    /**
     * Android has no `/etc/resolv.conf` to share, and a fresh root filesystem has none that works
     * (Ubuntu's is a link to a file that does not exist), so `ssh` could not resolve a host name.
     * Writes public resolvers only when the file is missing or empty; a file the user set up stays.
     */
    private suspend fun ensureResolver(distro: Distro) {
        val path = distro.directory.child("etc").getOrNull()?.child(RESOLV_CONF)?.getOrNull()
        val current = path?.let { files.read(it).getOrNull() }
        if (path != null && (current == null || current.isEmpty())) {
            files.write(path, DEFAULT_RESOLVERS.toByteArray(Charsets.US_ASCII), ownerOnly = false)
        }
    }

    private suspend fun release(file: KeyFile) {
        files.delete(file.path)
        live -= file.name
    }

    private suspend fun removeStaleKeys(tmp: FsPath?) {
        val names = tmp?.let { files.listNames(it).getOrNull() }.orEmpty()
        names.filter { it.startsWith(KEY_PREFIX) && it !in live }.forEach { name ->
            tmp?.child(name)?.getOrNull()?.let { files.delete(it) }
        }
    }

    private class KeyFile(val name: String, val path: FsPath)

    private companion object {
        const val TMP = "tmp"
        const val KEY_PREFIX = ".ut-ssh-"
        const val TOKEN_BYTES = 8
        const val RESOLV_CONF = "resolv.conf"
        const val DEFAULT_RESOLVERS = "nameserver 1.1.1.1\nnameserver 9.9.9.9\n"
    }
}
