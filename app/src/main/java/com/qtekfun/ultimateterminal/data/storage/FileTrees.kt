// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.FsPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Path resolution and walks of whole trees. None of them follows a symbolic link: a link is copied,
 * deleted or skipped as a link, never as what it points to.
 */
internal object FileTrees {
    private const val SEPARATOR = '/'

    /** The location of [path] under [root], refusing it if a symbolic link lies on the way. */
    fun resolveInside(root: Path, path: FsPath): Outcome<Path> {
        val segments = path.value.split(SEPARATOR)
        var current = root
        for (segment in segments.dropLast(1)) {
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                return Outcome.Failure(DomainError.InvalidPath(path.value))
            }
        }
        return Outcome.Success(current.resolve(segments.last()))
    }

    /**
     * Copies [source] to the new path [destination], keeping links, permissions and times.
     * Special files (sockets, pipes, devices) are skipped.
     */
    fun copy(source: Path, destination: Path) {
        val modes = HashMap<Path, Int>()
        TreeWalker.walk(
            source,
            object : TreeVisitor {
                override fun enterDirectory(dir: Path, attrs: BasicFileAttributes) {
                    Files.createDirectory(destination.resolve(source.relativize(dir).toString()))
                    modes[dir] = OwnerAccess.modeOf(dir)
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes) {
                    val copy = destination.resolve(source.relativize(file).toString())
                    when {
                        attrs.isSymbolicLink ->
                            Files.createSymbolicLink(copy, Files.readSymbolicLink(file))

                        attrs.isRegularFile -> copyRegular(file, copy)
                    }
                }

                // Attributes go on last: a read-only directory must stay writable while it is filled.
                override fun leaveDirectory(dir: Path) {
                    val copy = destination.resolve(source.relativize(dir).toString())
                    // The time first: the mode may leave nobody able to open the directory.
                    Files.setLastModifiedTime(copy, Files.getLastModifiedTime(dir))
                    runCatching { OwnerAccess.setMode(copy, modes.getValue(dir)) }
                }
            }
        )
    }

    /**
     * Copies one regular file with its mode, also when the app cannot read it (a mode 000 file):
     * the source is opened for its owner just while it is copied, and the copy gets the original.
     */
    private fun copyRegular(file: Path, copy: Path) {
        val mode = OwnerAccess.modeOf(file)
        OwnerAccess.reading(file) { input -> Files.copy(input, copy) }
        Files.setLastModifiedTime(copy, Files.getLastModifiedTime(file))
        OwnerAccess.setMode(copy, mode)
    }

    /** Deletes [target] and everything under it, even inside read-only or closed directories. */
    fun delete(target: Path) {
        // Walked in order, so a directory is emptied before it is removed.
        TreeWalker.walk(
            target,
            object : TreeVisitor {
                override fun visitFile(file: Path, attrs: BasicFileAttributes) {
                    Files.delete(file)
                }

                override fun leaveDirectory(dir: Path) {
                    Files.delete(dir)
                }
            },
            TreeWalker.Purpose.DELETE
        )
    }

    /**
     * Total size in bytes of the regular files under [target]. What cannot be listed (a directory
     * without permissions) is left out: a size is an estimate, not a reason to fail.
     */
    fun size(target: Path): Long {
        var total = 0L
        TreeWalker.walk(
            target,
            object : TreeVisitor {
                override fun visitFile(file: Path, attrs: BasicFileAttributes) {
                    if (attrs.isRegularFile) total += attrs.size()
                }
            },
            skipUnreadable = true
        )
        return total
    }
}
