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
    private val ioDispatcher: CoroutineDispatcher,
    private val freeSpace: (Path) -> Long
) : FileSystemRepository {
    override suspend fun exists(path: FsPath): Boolean = withContext(ioDispatcher) {
        val target = FileTrees.resolveInside(root, path).getOrNull()
        target != null && Files.exists(target, LinkOption.NOFOLLOW_LINKS)
    }

    override suspend fun createDirectories(path: FsPath): Outcome<Unit> = io(ioDispatcher) {
        FileTrees.resolveInside(root, path).flatMap { target ->
            Files.createDirectories(target)
            Outcome.Success(Unit)
        }
    }

    override suspend fun deleteRecursively(path: FsPath): Outcome<Unit> = io(ioDispatcher) {
        FileTrees.resolveInside(root, path).flatMap { target ->
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) FileTrees.delete(target)
            Outcome.Success(Unit)
        }
    }

    override suspend fun copyRecursively(from: FsPath, to: FsPath): Outcome<Unit> =
        io(ioDispatcher) {
            FileTrees.resolveInside(root, from).flatMap { source ->
                FileTrees.resolveInside(root, to).flatMap { target ->
                    AtomicCopy.copy(source, target)
                }
            }
        }

    override suspend fun move(from: FsPath, to: FsPath): Outcome<Unit> = io(ioDispatcher) {
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

    override suspend fun readText(path: FsPath): Outcome<String> = io(ioDispatcher) {
        FileTrees.resolveInside(root, path).flatMap { target ->
            val attributes = runCatching {
                Files.readAttributes(
                    target,
                    java.nio.file.attribute.BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS
                )
            }.getOrNull()
            when {
                attributes == null -> Outcome.Failure(DomainError.NotFound)

                !attributes.isRegularFile -> Outcome.Failure(DomainError.Io("not a regular file"))

                attributes.size() > FileSystemRepository.MAX_TEXT_BYTES ->
                    Outcome.Failure(DomainError.Io("file too large"))

                else -> try {
                    Outcome.Success(
                        Charsets.UTF_8.newDecoder().decode(
                            java.nio.ByteBuffer.wrap(Files.readAllBytes(target))
                        ).toString()
                    )
                } catch (_: java.nio.charset.CharacterCodingException) {
                    Outcome.Failure(DomainError.Io("not valid UTF-8 text"))
                }
            }
        }
    }

    override suspend fun writeText(path: FsPath, text: String): Outcome<Unit> = io(ioDispatcher) {
        FileTrees.resolveInside(root, path).flatMap { target ->
            val partial = target.resolveSibling("${target.fileName}$PARTIAL_SUFFIX")
            try {
                Files.deleteIfExists(partial)
                Files.write(partial, text.toByteArray(Charsets.UTF_8))
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    runCatching {
                        Files.setPosixFilePermissions(
                            partial,
                            Files.getPosixFilePermissions(target, LinkOption.NOFOLLOW_LINKS)
                        )
                    }
                }
                Files.move(
                    partial,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                Outcome.Success(Unit)
            } catch (e: IOException) {
                runCatching { Files.deleteIfExists(partial) }
                Outcome.Failure(DomainError.Io(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    override suspend fun sizeOf(path: FsPath): Outcome<Long> = io(ioDispatcher) {
        FileTrees.resolveInside(root, path).flatMap { target ->
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Outcome.Success(FileTrees.size(target))
            } else {
                Outcome.Failure(DomainError.NotFound)
            }
        }
    }

    /**
     * The probe is injected: on a device it is `StatFs` (see `AndroidFreeSpace`). `Files.getFileStore`
     * is not an option, since on Android it reads `/proc/mounts`, which SELinux denies to apps, and
     * throws `SecurityException` (found on a Pixel 8, where it crashed the install of every distro).
     * A directory that does not exist reports 0.
     */
    override suspend fun freeSpaceBytes(): Long = withContext(ioDispatcher) {
        try {
            freeSpace(root)
        } catch (_: SecurityException) {
            0L
        } catch (_: IllegalArgumentException) {
            0L
        }
    }

    override fun absolutePathOf(path: FsPath): String = root.resolve(path.value).toString()

    private companion object {
        const val PARTIAL_SUFFIX = AtomicCopy.PARTIAL_SUFFIX
    }
}

private suspend fun <T> io(dispatcher: CoroutineDispatcher, block: () -> Outcome<T>): Outcome<T> =
    withContext(dispatcher) {
        try {
            block()
        } catch (e: IOException) {
            Outcome.Failure(DomainError.Io(e.message ?: e.javaClass.simpleName))
        }
    }
