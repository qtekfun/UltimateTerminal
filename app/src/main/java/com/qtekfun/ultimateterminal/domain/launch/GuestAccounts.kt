// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

/** A user as the guest's `/etc/passwd` describes it. */
data class GuestAccount(
    val name: String,
    val uid: Int,
    val gid: Int,
    val home: String,
    /** The login shell to run; never a `nologin`/`false` program (see [GuestAccounts.loginShell]). */
    val shell: String
)

/** What adding a user changes: the whole new text of the two files. */
data class AccountFiles(val passwd: String, val group: String, val account: GuestAccount)

/**
 * Reads and extends the guest's `/etc/passwd` and `/etc/group`. Pure text in, text out: the
 * repository does the reading and the writing. It is how the app runs a session as a user without
 * `su`, which some minimal images (Fedora) do not ship.
 */
object GuestAccounts {
    const val DEFAULT_SHELL = "/bin/sh"
    private const val BASH = "/bin/bash"
    const val FIRST_USER_ID = 1000

    /** Ids from 60000 up are reserved on most distros (65534 is `nobody`). */
    private const val LAST_USER_ID = 59_999
    private const val PASSWD_FIELDS = 7
    private const val PASSWD_UID = 2
    private const val PASSWD_GID = 3
    private const val PASSWD_HOME = 5
    private const val PASSWD_SHELL = 6
    private const val GROUP_FIELDS = 3
    private const val GROUP_GID = 2
    private val NO_LOGIN = setOf("nologin", "false", "true")

    /** The entry for [name], or null if there is none (comments and broken lines are skipped). */
    fun find(passwd: String, name: String): GuestAccount? =
        entries(passwd).firstOrNull { it.name == name }

    /**
     * The shell to run for a passwd shell field: it must be an absolute path and not one of the
     * programs that only refuse a login, otherwise `/bin/sh`.
     */
    fun loginShell(field: String): String {
        val shell = field.trim()
        return if (!shell.startsWith("/") || shell.substringAfterLast('/') in NO_LOGIN) {
            DEFAULT_SHELL
        } else {
            shell
        }
    }

    /**
     * Adds [name] with the first free id from 1000, a group of the same name (reusing it if it
     * exists), `/home/<name>` and [shell]. Returns null if [name] is not valid, already exists, or
     * no id is free.
     */
    fun add(
        passwd: String,
        group: String,
        name: String,
        shell: String = DEFAULT_SHELL
    ): AccountFiles? {
        val users = entries(passwd)
        val groups = groupEntries(group)
        val usable =
            GuestUser.isValid(name) && !GuestUser.isRoot(name) && users.none { it.name == name }
        val uid = firstFree(users.map { it.uid }.toSet(), FIRST_USER_ID)
        val existing = groups.firstOrNull { it.first == name }?.second
        val usedGids = groups.map { it.second }.toSet() + users.map { it.gid }
        val gid =
            existing ?: uid?.takeUnless { it in usedGids } ?: firstFree(usedGids, FIRST_USER_ID)
        return if (!usable || uid == null || gid == null) {
            null
        } else {
            val account = GuestAccount(name, uid, gid, GuestUser.homeOf(name), loginShell(shell))
            AccountFiles(
                passwd = withLine(
                    passwd,
                    "$name:x:$uid:$gid:$name:${account.home}:${account.shell}"
                ),
                group = if (existing != null) group else withLine(group, "$name:x:$gid:"),
                account = account
            )
        }
    }

    private fun firstFree(used: Set<Int>, from: Int): Int? =
        (from..LAST_USER_ID).firstOrNull { it !in used }

    /** `/bin/bash` when `/etc/shells` lists it (a nicer prompt and history), else `/bin/sh`. */
    fun preferredShell(shells: String): String =
        if (shells.lineSequence().any { it.trim() == BASH }) BASH else DEFAULT_SHELL

    private fun withLine(text: String, line: String): String =
        if (text.isEmpty() || text.endsWith("\n")) "$text$line\n" else "$text\n$line\n"

    private fun entries(passwd: String): List<GuestAccount> =
        passwd.lineSequence().mapNotNull { raw ->
            val fields = raw.split(':')
            val uid = fields.getOrNull(PASSWD_UID)?.toIntOrNull()
            val gid = fields.getOrNull(PASSWD_GID)?.toIntOrNull()
            val wellFormed = !raw.startsWith("#") && fields.size >= PASSWD_FIELDS
            if (!wellFormed || uid == null || gid == null) {
                null
            } else {
                GuestAccount(
                    name = fields[0],
                    uid = uid,
                    gid = gid,
                    home =
                        fields[PASSWD_HOME].takeIf { it.startsWith("/") }
                            ?: GuestUser.homeOf(fields[0]),
                    shell = loginShell(fields[PASSWD_SHELL])
                )
            }
        }.toList()

    private fun groupEntries(group: String): List<Pair<String, Int>> =
        group.lineSequence().mapNotNull { raw ->
            val fields = raw.split(':')
            val gid = fields.getOrNull(GROUP_GID)?.toIntOrNull()
            if (raw.startsWith("#") || fields.size < GROUP_FIELDS || gid == null) {
                null
            } else {
                fields[0] to gid
            }
        }.toList()
}
