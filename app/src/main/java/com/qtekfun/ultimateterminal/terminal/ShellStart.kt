// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import com.qtekfun.ultimateterminal.data.proot.ProotLaunch
import com.qtekfun.ultimateterminal.domain.terminal.ShellEnvironment

/** The process a terminal host starts on its pty: the program, its arguments, directory and environment. */
data class ShellStart(
    val executable: String,
    /** The full argument vector, program name first, as `execve` takes it. */
    val arguments: List<String>,
    val workingDirectory: String,
    val environment: Array<String>
) {
    // An array in a data class compares by identity; compare the contents so equal starts are equal.
    override fun equals(other: Any?): Boolean = other is ShellStart &&
        executable == other.executable && arguments == other.arguments &&
        workingDirectory == other.workingDirectory && environment.contentEquals(other.environment)

    override fun hashCode(): Int =
        listOf(executable, arguments, workingDirectory, environment.toList()).hashCode()

    companion object {
        const val ANDROID_SHELL = "/system/bin/sh"
        private const val ANDROID_SHELL_NAME = "sh"

        /** Android's own shell, as the app has always opened it. */
        fun androidShell(home: String, tmp: String, inherited: Map<String, String>) = ShellStart(
            executable = ANDROID_SHELL,
            arguments = listOf(ANDROID_SHELL_NAME),
            workingDirectory = home,
            environment = ShellEnvironment.build(home, tmp, inherited)
        )

        /**
         * proot, which then runs the distro. The host process gets Android's runtime variables and
         * proot's own ([ProotLaunch.environment], which wins); the guest gets a clean environment
         * from `env -i` in the command line.
         */
        fun proot(
            launch: ProotLaunch,
            home: String,
            tmp: String,
            inherited: Map<String, String>
        ): ShellStart {
            val base = ShellEnvironment.build(home, tmp, inherited).associate { entry ->
                entry.substringBefore('=') to entry.substringAfter('=')
            }
            val merged = (base + launch.environment).toSortedMap().map { (key, value) ->
                "$key=$value"
            }
            return ShellStart(
                executable = launch.command.first(),
                arguments = launch.command,
                workingDirectory = home,
                environment = merged.toTypedArray()
            )
        }
    }
}
