// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Copies a tree next to its destination and renames it into place: all or nothing. */
internal object AtomicCopy {
    const val PARTIAL_SUFFIX = ".partial"

    fun copy(source: Path, target: Path): Outcome<Unit> {
        val partial = target.resolveSibling("${target.fileName}$PARTIAL_SUFFIX")
        return when {
            !Files.exists(
                source,
                LinkOption.NOFOLLOW_LINKS
            ) -> Outcome.Failure(DomainError.NotFound)

            Files.exists(target, LinkOption.NOFOLLOW_LINKS) ->
                Outcome.Failure(DomainError.Io("destination exists"))

            else -> try {
                if (Files.exists(partial, LinkOption.NOFOLLOW_LINKS)) FileTrees.delete(partial)
                target.parent?.let { Files.createDirectories(it) }
                FileTrees.copy(source, partial)
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
                Outcome.Success(Unit)
            } catch (e: IOException) {
                runCatching {
                    if (Files.exists(partial, LinkOption.NOFOLLOW_LINKS)) FileTrees.delete(partial)
                }
                Outcome.Failure(DomainError.Io(e.message ?: e.javaClass.simpleName))
            }
        }
    }
}
