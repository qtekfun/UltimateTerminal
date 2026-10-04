// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [FileSystemRepository] over `java.nio` under [root]. Trees are walked without following links,
 * and a path whose parents include a symbolic link is refused, so a link inside a root filesystem
 * (such as an absolute `/var/run -> /run`) can never lead an operation out of the storage.
 */
class NioFileSystemRepository(
    private val root: Path,
    private val ioDispatcher: CoroutineDispatcher
) : FileSystemRepository {
    override suspend fun exists(path: FsPath): Boolean = withContext(ioDispatcher) {
        val target = FileTrees.resolveInside(root, path).getOrNull()
        target != null && Files.exists(target, LinkOption.NOFOLLOW_LINKS)
    }

    override suspend fun createDirectories(path: FsPath): Outcome<Unit> = io {
        FileTrees.resolveInside(root, path).flatMap { target ->
            Files.createDirectories(target)
            Outcome.Success(Unit)
        }
    }

    override suspend fun deleteRecursively(path: FsPath): Outcome<Unit> = io {
        FileTrees.resolveInside(root, path).flatMap { target ->
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) FileTrees.delete(target)
            Outcome.Success(Unit)
        }
    }

    override suspend fun copyRecursively(from: FsPath, to: FsPath): Outcome<Unit> = io {
        FileTrees.resolveInside(root, from).flatMap { source ->
            FileTrees.resolveInside(root, to).flatMap { target -> copyAtomically(source, target) }
        }
    }

    override suspend fun move(from: FsPath, to: FsPath): Outcome<Unit> = io {
        FileTrees.resolveInside(root, from).flatMap { source ->
            FileTrees.resolveInside(root, to).flatMap { target ->
                when {
                    !Files.exists(
                        source,
                        LinkOption.NOFOLLOW_LINKS
                    ) -> Outcome.Failure(DomainError.NotFound)

                    Files.exists(target, LinkOption.NOFOLLOW_LINKS) ->
                        Outcome.Failure(DomainError.Io("destination exists"))

                    else -> {
                        target.parent?.let { Files.createDirectories(it) }
                        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
                        Outcome.Success(Unit)
                    }
                }
            }
        }
    }

    override suspend fun sizeOf(path: FsPath): Outcome<Long> = io {
        FileTrees.resolveInside(root, path).flatMap { target ->
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Outcome.Success(FileTrees.size(target))
            } else {
                Outcome.Failure(DomainError.NotFound)
            }
        }
    }

    /**
     * `File.usableSpace` goes through `statvfs`. `Files.getFileStore` is not an option: on Android
     * it reads `/proc/mounts`, which SELinux denies to apps, and throws `SecurityException`
     * (found on a Pixel 8, where it crashed the install of every distro). A missing directory
     * reports 0.
     */
    override suspend fun freeSpaceBytes(): Long = withContext(ioDispatcher) {
        try {
            root.toFile().usableSpace
        } catch (_: SecurityException) {
            0L
        }
    }

    override fun absolutePathOf(path: FsPath): String = root.resolve(path.value).toString()

    private suspend fun <T> io(block: () -> Outcome<T>): Outcome<T> = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: IOException) {
            Outcome.Failure(DomainError.Io(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun copyAtomically(source: Path, target: Path): Outcome<Unit> {
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

    private companion object {
        const val PARTIAL_SUFFIX = ".partial"
    }
}
