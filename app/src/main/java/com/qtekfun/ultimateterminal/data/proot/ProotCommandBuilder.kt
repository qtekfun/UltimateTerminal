// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.proot

import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.launch.ShellQuote

/** Where the guest sees a host directory. */
data class ProotBind(val hostPath: String, val guestPath: String = hostPath)

/** What to run inside the distro. */
data class ProotSession(
    /** Host path of the extracted root filesystem. */
    val rootfs: String,
    val shell: List<String> = DEFAULT_SHELL,
    val workingDirectory: String = "/root",
    val binds: List<ProotBind> = DEFAULT_BINDS,
    /** Pretend to be root (uid 0) inside the distro, which is what apt/apk expect. */
    val fakeRoot: Boolean = true,
    /**
     * Run without the seccomp filter, slower but needed on some kernels where the filter makes
     * proot misbehave. Off by default.
     */
    val disableSeccomp: Boolean = false,
    val term: String = "xterm-256color",
    /**
     * The user the shell runs as, null for root. A user other than root goes through `su -l`, which
     * needs the fake root (`-0`) to be allowed to change identity.
     */
    val user: String? = null,
    /**
     * A program and arguments to run instead of [shell] (an argument list, never a shell line). For
     * a user other than root it is quoted once, because `su -c` takes a single string.
     */
    val command: List<String>? = null
) {
    companion object {
        val DEFAULT_SHELL = listOf("/bin/sh", "-l")
        val DEFAULT_BINDS = listOf(ProotBind("/dev"), ProotBind("/proc"), ProotBind("/sys"))
    }
}

/**
 * Builds the proot command line and environment for a [ProotSession].
 *
 * proot and its loader ship as `libproot.so` and `libproot-loader.so` in the app's native
 * library directory, the only place Android lets an app execute files from at targetSdk 28.
 *
 * @param nativeLibraryDir `ApplicationInfo.nativeLibraryDir`.
 * @param tmpDir a private, writable directory for proot's temporary files (`PROOT_TMP_DIR`);
 *   the default `/tmp` does not exist on Android.
 */
class ProotCommandBuilder(private val nativeLibraryDir: String, private val tmpDir: String) {
    fun build(session: ProotSession): ProotLaunch {
        val command = buildList {
            add("$nativeLibraryDir/$PROOT_BINARY")
            // Hard links are not allowed in the app's storage; proot emulates them with symlinks.
            add("--link2symlink")
            // Do not leave tracees behind when the session ends.
            add("--kill-on-exit")
            if (session.fakeRoot) add("-0")
            add("-r")
            add(session.rootfs)
            session.binds.forEach { bind ->
                add("-b")
                add("${bind.hostPath}:${bind.guestPath}")
            }
            add("-w")
            add(session.workingDirectory)
            // A clean guest environment: the host process environment is Android's, not Linux's.
            addAll(
                listOf(
                    "/usr/bin/env",
                    "-i",
                    "HOME=${homeOf(session)}",
                    "TERM=${session.term}",
                    "LANG=C.UTF-8",
                    "PATH=$GUEST_PATH"
                )
            )
            addAll(guestCommand(session))
        }
        val environment = buildMap {
            put("PROOT_LOADER", "$nativeLibraryDir/$LOADER_BINARY")
            put("PROOT_TMP_DIR", tmpDir)
            if (session.disableSeccomp) put("PROOT_NO_SECCOMP", "1")
        }
        return ProotLaunch(command, environment)
    }

    private fun homeOf(session: ProotSession) = if (session.user != null) {
        GuestUser.homeOf(session.user)
    } else if (session.fakeRoot) {
        "/root"
    } else {
        "/home"
    }

    /** What the guest runs: the shell or the command, as root or through `su -l <user>`. */
    private fun guestCommand(session: ProotSession): List<String> {
        val command = session.command
        return if (GuestUser.isRoot(session.user)) {
            command ?: session.shell
        } else {
            val login = listOf("su", "-l", requireNotNull(session.user))
            if (command == null) login else login + listOf("-c", ShellQuote.join(command))
        }
    }

    companion object {
        const val PROOT_BINARY = "libproot.so"
        const val LOADER_BINARY = "libproot-loader.so"
        const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    }
}
