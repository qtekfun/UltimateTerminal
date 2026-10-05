// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.data.storage.OwnerAccess
import com.qtekfun.ultimateterminal.data.storage.TreeVisitor
import com.qtekfun.ultimateterminal.data.storage.TreeWalker
import com.qtekfun.ultimateterminal.data.storage.UnreadableFileException
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants

/**
 * Writes a root filesystem as a gzip-compressed tar, keeping what a distro needs to work again:
 * the permission bits (so executables stay executable, setuid ones included where the file system
 * reports them), symbolic links as links and the modification times. A file or directory that the
 * app cannot read (a distro's `/etc/shadow` is mode 000) is made readable for the owner just while
 * it is read, and its mode is put back: the archive records the original mode. A link is never
 * followed. A file that cannot be read at all fails the whole export with
 * [BackupError.UnreadableFile] naming it, so no incomplete backup is ever written. Special files
 * (sockets, pipes, devices) are skipped, as the installer's extractor would skip them. Every entry
 * is owned by root: the files belong to the app's user on the device, and proot presents ownership
 * itself.
 */
internal class RootfsArchiver(private val open: (Path) -> InputStream = Files::newInputStream) {
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
        try {
            tar.use { TreeWalker.walk(root, TreeWriter(root, tar, open, onFile)) }
        } catch (e: UnreadableFileException) {
            val name = root.relativize(e.path).toString()
            throw BackupFailure(BackupError.UnreadableFile(name, e.denied)).apply { initCause(e) }
        }
    }

    private class TreeWriter(
        private val root: Path,
        private val tar: TarArchiveOutputStream,
        private val open: (Path) -> InputStream,
        private val onFile: (Long) -> Unit
    ) : TreeVisitor {
        private var contentBytes = 0L

        override fun enterDirectory(dir: Path, attrs: BasicFileAttributes) {
            val entry = TarArchiveEntry(nameOf(dir, directory = true))
            entry.mode = OwnerAccess.modeOf(dir)
            entry.setModTime(attrs.lastModifiedTime())
            tar.putArchiveEntry(entry)
            tar.closeArchiveEntry()
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes) {
            when {
                attrs.isSymbolicLink -> link(file)
                attrs.isRegularFile -> regular(file, attrs)
                // else: a socket, a pipe or a device
            }
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
            entry.mode = OwnerAccess.modeOf(file)
            entry.setModTime(attrs.lastModifiedTime())
            OwnerAccess.reading(file, open) { input ->
                tar.putArchiveEntry(entry)
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    tar.write(buffer, 0, read)
                    contentBytes += read
                    onFile(contentBytes)
                }
                tar.closeArchiveEntry()
            }
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
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
        const val SYMLINK_MODE = 0b111_111_111
    }
}
