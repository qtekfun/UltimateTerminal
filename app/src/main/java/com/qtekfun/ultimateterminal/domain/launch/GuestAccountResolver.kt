// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository

/** The user a session runs as and the directory it starts in. */
data class GuestLogin(val account: GuestAccount, val workingDirectory: String)

/**
 * Finds the configured user in the rootfs' `/etc/passwd`, creating it when it is missing (D-USER-2),
 * so a session never starts as a user that does not exist. Everything goes through the
 * [FileSystemRepository]; the text work is [GuestAccounts].
 */
class GuestAccountResolver(private val fileSystem: FileSystemRepository) {
    /** The login for [name] in the rootfs at [rootfs], or null when it can neither be read nor created. */
    suspend fun resolve(rootfs: FsPath, name: String): GuestLogin? {
        val passwdPath = rootfs.child(PASSWD).getOrNull()
        val groupPath = rootfs.child(GROUP).getOrNull()
        val passwd = passwdPath?.let { fileSystem.readText(it) } as? Outcome.Success
        if (passwdPath == null || groupPath == null || passwd == null) return null
        val account = GuestAccounts.find(passwd.value, name) ?: create(
            rootfs,
            passwdPath,
            groupPath,
            passwd.value,
            name
        )
        return account?.let { GuestLogin(it, workingDirectoryOf(rootfs, it)) }
    }

    /**
     * Writes the group first and the passwd entry last: a group without a user is harmless, so a
     * failure between the two leaves nothing that changes how any user logs in. Each file is
     * replaced whole (see [FileSystemRepository.writeText]).
     */
    private suspend fun create(
        rootfs: FsPath,
        passwdPath: FsPath,
        groupPath: FsPath,
        passwd: String,
        name: String
    ): GuestAccount? {
        val group = readGroup(groupPath)
        val shells = rootfs.child(SHELLS).getOrNull()?.let { fileSystem.readText(it) }
        val shell = GuestAccounts.preferredShell((shells as? Outcome.Success)?.value.orEmpty())
        val files = group?.let { GuestAccounts.add(passwd, it, name, shell) }
        val written = files != null &&
            fileSystem.writeText(groupPath, files.group) is Outcome.Success &&
            fileSystem.writeText(passwdPath, files.passwd) is Outcome.Success
        return files?.account?.takeIf { written }
    }

    /** The group file's text; empty when it does not exist; null when it exists but cannot be read. */
    private suspend fun readGroup(groupPath: FsPath): String? =
        when (val read = fileSystem.readText(groupPath)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> "".takeUnless { fileSystem.exists(groupPath) }
        }

    /** The home when it exists (created under `/home`), else `/`, since `-w` needs a real directory. */
    private suspend fun workingDirectoryOf(rootfs: FsPath, account: GuestAccount): String {
        val relative = account.home.removePrefix("/")
        val home = FsPath.of("${rootfs.value}/$relative").getOrNull()
        if (home == null) return "/"
        if (account.home.startsWith("/home/")) fileSystem.createDirectories(home)
        return if (fileSystem.exists(home)) account.home else "/"
    }

    private companion object {
        const val PASSWD = "etc/passwd"
        const val GROUP = "etc/group"
        const val SHELLS = "etc/shells"
    }
}
