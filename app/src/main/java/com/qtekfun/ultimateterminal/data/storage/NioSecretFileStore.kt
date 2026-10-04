// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.SecretFileStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [SecretFileStore] over `java.nio` under [root], the same root as the [NioFileSystemRepository].
 * Paths go through [FileTrees.resolveInside], so a symbolic link on the way (a `/tmp` that is a link
 * in somebody else's root filesystem) can never send a write outside the storage.
 */
class NioSecretFileStore(private val root: Path, private val ioDispatcher: CoroutineDispatcher) :
    SecretFileStore {
    override suspend fun write(path: FsPath, bytes: ByteArray, ownerOnly: Boolean): Outcome<Unit> =
        io {
            FileTrees.resolveInside(root, path).flatMap { target ->
                writeAtomically(target, bytes, ownerOnly)
                Outcome.Success(Unit)
            }
        }

    override suspend fun read(path: FsPath): Outcome<ByteArray> = io {
        FileTrees.resolveInside(root, path).flatMap { target ->
            if (Files.isRegularFile(target)) {
                Outcome.Success(Files.readAllBytes(target))
            } else {
                Outcome.Failure(DomainError.NotFound)
            }
        }
    }

    override suspend fun listNames(directory: FsPath): Outcome<List<String>> = io {
        FileTrees.resolveInside(root, directory).flatMap { target ->
            if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                Outcome.Success(emptyList())
            } else {
                Files.newDirectoryStream(target).use { stream ->
                    Outcome.Success(stream.map { it.fileName.toString() }.sorted())
                }
            }
        }
    }

    override suspend fun delete(path: FsPath): Outcome<Unit> = io {
        FileTrees.resolveInside(root, path).flatMap { target ->
            Files.deleteIfExists(target)
            Outcome.Success(Unit)
        }
    }

    // The file is created with its final permissions and only then filled, so the bytes are never
    // readable by anyone else, even for an instant; the rename makes the new content appear at once.
    private fun writeAtomically(target: Path, bytes: ByteArray, ownerOnly: Boolean) {
        target.parent?.let { Files.createDirectories(it) }
        val partial = target.resolveSibling("${target.fileName}$PARTIAL_SUFFIX")
        Files.deleteIfExists(partial)
        try {
            if (ownerOnly) {
                val permissions = PosixFilePermissions.fromString(OWNER_ONLY)
                Files.createFile(partial, PosixFilePermissions.asFileAttribute(permissions))
            } else {
                Files.createFile(partial)
            }
            Files.write(partial, bytes)
            Files.move(
                partial,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    private suspend fun <T> io(block: () -> Outcome<T>): Outcome<T> = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: IOException) {
            Outcome.Failure(DomainError.Io(e.message ?: e.javaClass.simpleName))
        } catch (_: UnsupportedOperationException) {
            // No POSIX permissions here: refuse rather than write a secret anyone could read.
            Outcome.Failure(DomainError.Io("owner-only files are not supported"))
        }
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".partial"
        const val OWNER_ONLY = "rw-------"
    }
}
