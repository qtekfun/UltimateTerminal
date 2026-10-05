// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.fakes

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository

/**
 * In-memory [FileSystemRepository] for tests of code that depends on it. Files are byte arrays and
 * directories are implied by the paths; it is checked against FileSystemRepositoryContract.
 * It does not model symbolic links or permissions.
 */
class InMemoryFileSystemRepository(
    private val freeSpace: Long = Long.MAX_VALUE,
    private val rootPath: String = "/storage"
) : FileSystemRepository {
    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf<String>()

    /** Adds a file (and its parent directories) so tests can set up a tree. */
    fun putFile(path: String, content: ByteArray = ByteArray(0)) {
        files[path] = content
        parentsOf(path).forEach(directories::add)
    }

    fun readFile(path: String): ByteArray? = files[path]

    override suspend fun exists(path: FsPath): Boolean = existsRaw(path.value)

    override suspend fun createDirectories(path: FsPath): Outcome<Unit> {
        if (path.value in files) return Outcome.Failure(DomainError.Io("a file is in the way"))
        directories += path.value
        parentsOf(path.value).forEach(directories::add)
        return Outcome.Success(Unit)
    }

    override suspend fun deleteRecursively(path: FsPath): Outcome<Unit> {
        removeTree(path.value)
        return Outcome.Success(Unit)
    }

    override suspend fun copyRecursively(from: FsPath, to: FsPath): Outcome<Unit> = when {
        !existsRaw(from.value) -> Outcome.Failure(DomainError.NotFound)

        existsRaw(to.value) -> Outcome.Failure(DomainError.Io("destination exists"))

        else -> {
            transfer(from.value, to.value, keepSource = true)
            Outcome.Success(Unit)
        }
    }

    override suspend fun move(from: FsPath, to: FsPath): Outcome<Unit> = when {
        !existsRaw(from.value) -> Outcome.Failure(DomainError.NotFound)

        existsRaw(to.value) -> Outcome.Failure(DomainError.Io("destination exists"))

        else -> {
            transfer(from.value, to.value, keepSource = false)
            Outcome.Success(Unit)
        }
    }

    override suspend fun sizeOf(path: FsPath): Outcome<Long> = if (existsRaw(path.value)) {
        Outcome.Success(
            files.filterKeys {
                inTree(it, path.value)
            }.values.sumOf { it.size.toLong() }
        )
    } else {
        Outcome.Failure(DomainError.NotFound)
    }

    override suspend fun readText(path: FsPath): Outcome<String> {
        val content = files[path.value]
            ?: return Outcome.Failure(
                if (existsRaw(
                        path.value
                    )
                ) {
                    DomainError.Io("not a regular file")
                } else {
                    DomainError.NotFound
                }
            )
        return if (content.size > FileSystemRepository.MAX_TEXT_BYTES) {
            Outcome.Failure(DomainError.Io("file too large"))
        } else {
            Outcome.Success(content.toString(Charsets.UTF_8))
        }
    }

    override suspend fun writeText(path: FsPath, text: String): Outcome<Unit> {
        val parent = path.value.substringBeforeLast('/', "")
        return when {
            path.value in directories -> Outcome.Failure(
                DomainError.Io("a directory is in the way")
            )

            parent.isNotEmpty() && parent !in directories ->
                Outcome.Failure(DomainError.Io("no parent directory"))

            else -> {
                files[path.value] = text.toByteArray(Charsets.UTF_8)
                Outcome.Success(Unit)
            }
        }
    }

    override suspend fun freeSpaceBytes(): Long = freeSpace

    override fun absolutePathOf(path: FsPath): String = "$rootPath/${path.value}"

    private fun existsRaw(path: String) = path in files || path in directories

    private fun inTree(candidate: String, root: String) =
        candidate == root || candidate.startsWith("$root/")

    private fun parentsOf(path: String): List<String> {
        val segments = path.split('/')
        return (1 until segments.size).map { segments.take(it).joinToString("/") }
    }

    private fun removeTree(root: String) {
        files.keys.removeAll { inTree(it, root) }
        directories.removeAll { inTree(it, root) }
    }

    private fun transfer(from: String, to: String, keepSource: Boolean) {
        val moved = files.filterKeys { inTree(it, from) }.mapKeys { to + it.key.removePrefix(from) }
        val movedDirectories = directories.filter { inTree(it, from) }.map {
            to +
                it.removePrefix(from)
        }
        if (!keepSource) removeTree(from)
        moved.forEach { (path, content) -> putFile(path, content.copyOf()) }
        directories.addAll(movedDirectories)
        parentsOf(to).forEach(directories::add)
        if (from in directories || moved.isEmpty()) directories += to
    }
}
