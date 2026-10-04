// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.FsPath
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission

/**
 * Path resolution and walks of whole trees. None of them follows a symbolic link: a link is copied,
 * deleted or skipped as a link, never as what it points to.
 */
internal object FileTrees {
    private const val SEPARATOR = '/'
    private val ownerAll = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    )

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
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    Files.createDirectory(destination.resolve(source.relativize(dir).toString()))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val copy = destination.resolve(source.relativize(file).toString())
                    when {
                        attrs.isSymbolicLink ->
                            Files.createSymbolicLink(copy, Files.readSymbolicLink(file))

                        attrs.isRegularFile ->
                            Files.copy(file, copy, StandardCopyOption.COPY_ATTRIBUTES)
                    }
                    return FileVisitResult.CONTINUE
                }

                // Attributes go on last: a read-only directory must stay writable while it is filled.
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    val copy = destination.resolve(source.relativize(dir).toString())
                    runCatching {
                        Files.setPosixFilePermissions(copy, Files.getPosixFilePermissions(dir))
                    }
                    Files.setLastModifiedTime(copy, Files.getLastModifiedTime(dir))
                    return FileVisitResult.CONTINUE
                }
            }
        )
    }

    /** Deletes [target] and everything under it, even inside read-only directories. */
    fun delete(target: Path) {
        Files.walkFileTree(
            target,
            object : SimpleFileVisitor<Path>() {
                // A root filesystem has read-only directories; deleting inside one needs write access.
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    runCatching { Files.setPosixFilePermissions(dir, ownerAll) }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            }
        )
    }

    /** Total size in bytes of the regular files under [target]. */
    fun size(target: Path): Long {
        var total = 0L
        Files.walkFileTree(
            target,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) total += attrs.size()
                    return FileVisitResult.CONTINUE
                }
            }
        )
        return total
    }
}
