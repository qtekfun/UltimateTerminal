// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.model

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome

/**
 * A path relative to the app's private storage root, such as `distros/<id>`. It is a plain string
 * so that `ui` and `domain` never touch `java.io.File`. Absolute paths, empty segments and "." or
 * ".." are rejected, so a path can never point outside the root.
 */
@JvmInline
value class FsPath private constructor(val value: String) {
    /** The path of [child] inside this one, or a failure if [child] is not a plain file name. */
    fun child(child: String): Outcome<FsPath> = of("$value/$child")

    override fun toString(): String = value

    companion object {
        private const val SEPARATOR = '/'

        fun of(raw: String): Outcome<FsPath> {
            val unsafe = raw.isEmpty() ||
                raw.split(SEPARATOR).any { it.isEmpty() || it == "." || it == ".." } ||
                raw.any { it == '\\' || it == '\u0000' }
            return if (unsafe) {
                Outcome.Failure(DomainError.InvalidPath(raw))
            } else {
                Outcome.Success(FsPath(raw))
            }
        }
    }
}
