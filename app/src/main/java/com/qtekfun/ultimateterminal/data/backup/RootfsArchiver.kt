// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants

/**
 * Writes a root filesystem as a gzip-compressed tar, keeping what a distro needs to work again:
 * the nine permission bits (so executables stay executable), symbolic links as links and the
 * modification times. Special files (sockets, pipes, devices) are skipped, as the installer's
 * extractor would skip them. Every entry is owned by root: the files belong to the app's user on
 * the device, and proot presents ownership itself.
 */
internal class RootfsArchiver {
    /**
     * Archives the tree under [root] into [out], which is closed. [onFile] is told how many bytes
     * of file content went in so far, and may throw to stop (it is how cancellation gets in).
     */
    fun write(root: Path, out: OutputStream, onFile: (Long) -> Unit) {
        val tar = TarArchiveOutputStream(GZIPOutputStream(out)).apply {
            setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            setAddPaxHeadersForNonAsciiNames(true)
        }
        tar.use { Files.walkFileTree(root, TreeWriter(root, tar, onFile)) }
    }

    private class TreeWriter(
        private val root: Path,
        private val tar: TarArchiveOutputStream,
        private val onFile: (Long) -> Unit
    ) : SimpleFileVisitor<Path>() {
        private var contentBytes = 0L

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val entry = TarArchiveEntry(nameOf(dir, directory = true))
            entry.mode = modeOf(dir)
            entry.setModTime(attrs.lastModifiedTime())
            tar.putArchiveEntry(entry)
            tar.closeArchiveEntry()
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            when {
                attrs.isSymbolicLink -> link(file)
                attrs.isRegularFile -> regular(file, attrs)
                // else: a socket, a pipe or a device
            }
            return FileVisitResult.CONTINUE
        }

        private fun link(file: Path) {
            val entry = TarArchiveEntry(nameOf(file, directory = false), TarConstants.LF_SYMLINK)
            entry.linkName = Files.readSymbolicLink(file).toString()
            entry.mode = SYMLINK_MODE
            tar.putArchiveEntry(entry)
            tar.closeArchiveEntry()
        }

        private fun regular(file: Path, attrs: BasicFileAttributes) {
            val entry = TarArchiveEntry(nameOf(file, directory = false))
            entry.size = attrs.size()
            entry.mode = modeOf(file)
            entry.setModTime(attrs.lastModifiedTime())
            tar.putArchiveEntry(entry)
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    tar.write(buffer, 0, read)
                    contentBytes += read
                    onFile(contentBytes)
                }
            }
            tar.closeArchiveEntry()
        }

        /** The root is "./", as in the distros' own archives; the rest are relative paths. */
        private fun nameOf(path: Path, directory: Boolean): String {
            val relative = root.relativize(path).toString()
            return when {
                relative.isEmpty() -> "./"
                directory -> "$relative/"
                else -> relative
            }
        }

        private fun modeOf(path: Path): Int =
            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).fold(0) { mode, bit ->
                mode or (1 shl BITS.indexOf(bit))
            }
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
        const val SYMLINK_MODE = 0b111_111_111

        /** From the lowest bit up: others x, w, r, then group, then owner. */
        val BITS = listOf(
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
    }
}
