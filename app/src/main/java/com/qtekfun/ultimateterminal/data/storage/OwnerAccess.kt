// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import java.io.IOException
import java.io.InputStream
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/** A file or directory the app could not read, even after giving its owner (the app) access. */
class UnreadableFileException(val path: Path, cause: IOException) :
    FileSystemException(path.toString(), null, cause.message) {
    init {
        initCause(cause)
    }

    /** True when the system refused (permissions), as opposed to a file that vanished or broke. */
    val denied: Boolean get() = cause is AccessDeniedException
}

/**
 * Host-side reads of a root filesystem. The files belong to the app's user, but they keep the
 * modes of the distro, and a distro makes some of them unreadable for their owner (`/etc/shadow`
 * is mode 000 on Fedora, `/usr/bin/sudo` is execute-only): in a real Linux only root can read
 * them, and under proot the guest is root but the app, on the host, is not. The app owns them, so
 * it may add the owner's read (and, for directories, search) bit for the time it reads, and put
 * the mode back afterwards. Symbolic links are never followed or changed.
 */
internal object OwnerAccess {
    private const val OWNER_READ = 0b100_000_000
    private const val OWNER_EXEC = 0b001_000_000
    private const val ALL_BITS = 0b111_111_111_111
    private const val UNIX_MODE = "unix:mode"

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

    /**
     * The twelve mode bits of [path] (setuid, setgid and sticky included when the file system
     * reports them; otherwise only the nine rwx bits), without following a link.
     */
    fun modeOf(path: Path): Int = try {
        (Files.getAttribute(path, UNIX_MODE, LinkOption.NOFOLLOW_LINKS) as Int) and ALL_BITS
    } catch (_: UnsupportedOperationException) {
        nineBits(path)
    } catch (_: IllegalArgumentException) {
        nineBits(path)
    }

    /** Sets the twelve mode bits of [path], or the nine rwx bits where the rest cannot be set. */
    fun setMode(path: Path, mode: Int) {
        try {
            Files.setAttribute(path, UNIX_MODE, mode and ALL_BITS, LinkOption.NOFOLLOW_LINKS)
        } catch (_: UnsupportedOperationException) {
            Files.setPosixFilePermissions(path, permissionsOf(mode))
        } catch (_: IllegalArgumentException) {
            Files.setPosixFilePermissions(path, permissionsOf(mode))
        }
    }

    /**
     * Makes the directory [dir] listable by its owner, for as long as the caller needs it. Returns
     * the owner bits to put back with [restore], or null when nothing was changed.
     */
    fun openDirectory(dir: Path): Int? {
        if (Files.isReadable(dir) && Files.isExecutable(dir)) return null
        try {
            val original = modeOf(dir)
            addOwner(dir, OWNER_READ or OWNER_EXEC)
            return original
        } catch (e: IOException) {
            throw UnreadableFileException(dir, e)
        }
    }

    /** Gives the owner rwx on the directory [dir] so its content can be removed (best effort). */
    fun openForDelete(dir: Path) {
        if (Files.isSymbolicLink(dir)) return
        val file = dir.toFile()
        file.setReadable(true, true)
        file.setWritable(true, true)
        file.setExecutable(true, true)
    }

    /** Puts the owner's read and search bits back as saved by [openDirectory] or [reading]. */
    fun restore(path: Path, original: Int?) {
        if (original == null) return
        // Only the owner's bits were ever added, and File changes just those (it does not open the
        // file, which a mode 000 file would refuse; the NIO attribute views do open it).
        val file = path.toFile()
        file.setReadable(original and OWNER_READ != 0, true)
        file.setExecutable(original and OWNER_EXEC != 0, true)
    }

    /**
     * Reads the regular file [file] with [use] on a stream from [open]. If it cannot be opened,
     * the owner's read bit is added for the read and the original mode is put back afterwards, so
     * the mode on disk (and in a backup) stays as it was. Throws [UnreadableFileException] when
     * even that does not work; errors raised by [use] itself pass through untouched.
     */
    fun <T> reading(
        file: Path,
        open: (Path) -> InputStream = Files::newInputStream,
        use: (InputStream) -> T
    ): T {
        var original: Int? = null
        try {
            val stream = try {
                open(file)
            } catch (_: IOException) {
                try {
                    val saved = modeOf(file)
                    addOwner(file, OWNER_READ)
                    original = saved
                    open(file)
                } catch (e: IOException) {
                    throw UnreadableFileException(file, e)
                }
            }
            return stream.use(use)
        } finally {
            restore(file, original)
        }
    }

    /** Adds [bits] of the owner to a file that is not a link, with chmod(2) on its path. */
    private fun addOwner(path: Path, bits: Int) {
        if (Files.isSymbolicLink(path)) throw IOException("a link is never changed: $path")
        val file = path.toFile()
        val ok = (bits and OWNER_READ == 0 || file.setReadable(true, true)) &&
            (bits and OWNER_EXEC == 0 || file.setExecutable(true, true))
        if (!ok) throw AccessDeniedException(path.toString())
    }

    private fun nineBits(path: Path): Int =
        Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).fold(0) { mode, bit ->
            mode or (1 shl BITS.indexOf(bit))
        }

    private fun permissionsOf(mode: Int): Set<PosixFilePermission> =
        BITS.filterIndexed { bit, _ -> mode and (1 shl bit) != 0 }.toSet()
}
