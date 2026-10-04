// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/** The environment of the shell started in a terminal tab. */
object ShellEnvironment {
    /** Variables passed through from the app's own environment: they locate Android's runtime. */
    private val inheritedKeys = listOf(
        "ANDROID_ROOT",
        "ANDROID_DATA",
        "ANDROID_ART_ROOT",
        "ANDROID_I18N_ROOT",
        "ANDROID_RUNTIME_ROOT",
        "ANDROID_TZDATA_ROOT",
        "EXTERNAL_STORAGE",
        "BOOTCLASSPATH"
    )

    private const val DEFAULT_PATH = "/system/bin:/system/xbin"

    /** Returns `KEY=VALUE` entries, sorted by key so the result does not depend on map order. */
    fun build(home: String, tmp: String, inherited: Map<String, String>): Array<String> {
        val variables = sortedMapOf(
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "LANG" to "en_US.UTF-8",
            "HOME" to home,
            "TMPDIR" to tmp,
            "PATH" to DEFAULT_PATH
        )
        inheritedKeys.forEach { key -> inherited[key]?.let { variables[key] = it } }
        return variables.map { (key, value) -> "$key=$value" }.toTypedArray()
    }
}
