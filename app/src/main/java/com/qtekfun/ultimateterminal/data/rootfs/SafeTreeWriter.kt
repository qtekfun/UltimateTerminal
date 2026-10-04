// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission

/** Thrown for an archive that must be refused; the extractor turns it into an [ExtractionError]. */
internal class ExtractionFailure(val error: ExtractionError) :
    RuntimeException(null, null, false, false)

/**
 * Writes the entries of an archive under [root] and nothing else: every path is resolved name by
 * name, a parent that is a symbolic link is refused, and a name with ".." is refused outright.
 * It holds no tar types, so these rules can be read (and tested) apart from the tar parsing.
 */
internal class SafeTreeWriter(private val root: Path, private val maxPathLength: Int) {
    private val verifiedDirectories = HashSet<Path>()
    private val directoryModes = ArrayList<Pair<Path, Int>>()

    fun begin() {
        Files.createDirectories(root)
        verifiedDirectories.add(root)
    }

    /**
     * The absolute path of [rawName] inside the destination, or null for the destination itself
     * (the `./` entry). Throws if it would leave the destination or pass through a link.
     */
    fun resolve(rawName: String): Path? {
        if (rawName.length > maxPathLength || rawName.contains('\u0000')) {
            throw EntryChecks.unsafe(rawName, "invalid name")
        }
        // Like GNU tar, drop leading slashes; anything with ".." is refused outright.
        val parts = rawName.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) throw EntryChecks.unsafe(rawName, "contains \"..\"")
        var current = root
        parts.forEachIndexed { index, part ->
            current = current.resolve(part)
            if (index < parts.lastIndex) ensureRealDirectory(current, rawName)
        }
        return current.takeIf { parts.isNotEmpty() }
    }

    /** A parent must be a real directory, never a link: following it could leave the destination. */
    private fun ensureRealDirectory(directory: Path, entryName: String) {
        if (directory in verifiedDirectories) return
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            when {
                Files.isSymbolicLink(directory) ->
                    throw EntryChecks.unsafe(entryName, "passes through a symbolic link")

                !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) ->
                    throw EntryChecks.unsafe(entryName, "a file is in the way")
            }
        } else {
            Files.createDirectory(directory)
        }
        verifiedDirectories.add(directory)
    }

    /** A directory entry; a null [target] is the destination itself, whose mode is applied last. */
    fun directory(name: String, target: Path?, mode: Int) {
        if (target == null) {
            directoryModes.add(root to mode)
            return
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw EntryChecks.unsafe(name, "replaces a file or link with a directory")
            }
        } else {
            Files.createDirectory(target)
        }
        verifiedDirectories.add(target)
        directoryModes.add(target to mode)
    }

    fun symlink(name: String, target: Path?, destination: String) {
        val path = EntryChecks.entryPath(name, target)
        if (destination.isEmpty() || destination.contains('\u0000')) {
            throw ExtractionFailure(ExtractionError.Corrupt("symbolic link without a target"))
        }
        EntryChecks.removeExisting(name, path)
        Files.createSymbolicLink(path, Paths.get(destination))
    }

    /** [sourceName] is the link's target as written in the archive; it is checked like any name. */
    fun hardLink(name: String, target: Path?, sourceName: String) {
        val path = EntryChecks.entryPath(name, target)
        val source = resolve(sourceName) ?: throw EntryChecks.unsafe(name, "hard link to the root")
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw EntryChecks.unsafe(name, "hard link to a file that is not there")
        }
        EntryChecks.removeExisting(name, path)
        try {
            Files.createLink(path, source)
        } catch (_: IOException) {
            // Some file systems refuse hard links; a copy keeps the content.
            Files.copy(source, path)
        } catch (_: UnsupportedOperationException) {
            Files.copy(source, path)
        }
    }

    /**
     * Writes a regular file from [content], calling [onChunk] with the size of each piece so the
     * caller can enforce its limits and notice a cancellation.
     */
    fun file(name: String, target: Path?, mode: Int, content: InputStream, onChunk: (Int) -> Unit) {
        val path = EntryChecks.entryPath(name, target)
        EntryChecks.removeExisting(name, path)
        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            .use { out ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = content.read(buffer)
                    if (read < 0) break
                    onChunk(read)
                    out.write(buffer, 0, read)
                }
            }
        Files.setPosixFilePermissions(path, FileModes.permissionsOf(mode))
    }

    /** Directories keep their modes only at the end, so a read-only one cannot block its children. */
    fun finish() {
        directoryModes.asReversed().forEach { (path, mode) ->
            Files.setPosixFilePermissions(path, FileModes.permissionsOf(mode) + FileModes.OWNER_ALL)
        }
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
    }
}

/** Turns the mode of a tar entry into permissions the app can apply without being root. */
internal object FileModes {
    val OWNER_ALL = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    )

    /** From the lowest bit up: others x, w, r, then group, then owner. */
    private val BITS = listOf(
        PosixFilePermission.OTHERS_EXECUTE,
        PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_READ
    )

    /** The nine rwx bits of a tar mode; the setuid, setgid and sticky bits are dropped. */
    fun permissionsOf(mode: Int): Set<PosixFilePermission> =
        BITS.filterIndexed { bit, _ -> mode and (1 shl bit) != 0 }.toSet()
}

/** Stateless checks shared by the writer's entry types. */
internal object EntryChecks {
    /** A later entry replaces an earlier one, as in tar; but never a directory by a non-directory. */
    fun removeExisting(name: String, path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw unsafe(name, "replaces a directory")
        }
        Files.delete(path)
    }

    /** Only a directory may name the destination itself; any other entry doing so is refused. */
    fun entryPath(name: String, target: Path?): Path =
        target ?: throw unsafe(name, "not a directory but named like the root")

    fun unsafe(name: String, reason: String) =
        ExtractionFailure(ExtractionError.UnsafeEntry(name, reason))
}
