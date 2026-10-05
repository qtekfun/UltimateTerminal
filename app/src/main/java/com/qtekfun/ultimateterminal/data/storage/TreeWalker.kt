// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.storage

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** What a [TreeWalker] reports; every function does nothing unless it is overridden. */
internal interface TreeVisitor {
    /** Before the content of [dir]; [attrs] and the mode are those it had before it was opened. */
    fun enterDirectory(dir: Path, attrs: BasicFileAttributes) {}

    /** After all the content of [dir]. */
    fun leaveDirectory(dir: Path) {}

    /** A regular file, a symbolic link (never followed) or a special file. */
    fun visitFile(file: Path, attrs: BasicFileAttributes) {}
}

/**
 * A walk of a whole tree that does not follow links, goes in a fixed order (by name, so an archive
 * made from it is the same every time) and gets past directories the owner cannot list: the
 * JDK's `walkFileTree` opens a directory before telling the visitor about it, too late to fix its
 * mode. A directory opened for reading is put back as it was; one opened for deletion is not.
 */
internal object TreeWalker {
    /** What a walk needs from the directories it goes through. */
    enum class Purpose { READ, DELETE }

    /**
     * Walks [root]. A directory that cannot be listed even after it was opened throws
     * [UnreadableFileException], unless [skipUnreadable] is set (a size is only an estimate).
     */
    fun walk(
        root: Path,
        visitor: TreeVisitor,
        purpose: Purpose = Purpose.READ,
        skipUnreadable: Boolean = false
    ) {
        visit(root, Files.readAttributes(root, BasicFileAttributes::class.java, NOFOLLOW), Walk(visitor, purpose, skipUnreadable))
    }

    private val NOFOLLOW = LinkOption.NOFOLLOW_LINKS

    private class Walk(
        val visitor: TreeVisitor,
        val purpose: Purpose,
        val skipUnreadable: Boolean
    )

    private fun visit(path: Path, attrs: BasicFileAttributes, walk: Walk) {
        if (attrs.isDirectory) directory(path, attrs, walk) else walk.visitor.visitFile(path, attrs)
    }

    private fun directory(dir: Path, attrs: BasicFileAttributes, walk: Walk) {
        walk.visitor.enterDirectory(dir, attrs)
        var saved: Int? = null
        try {
            val children = try {
                saved = open(dir, walk.purpose)
                names(dir)
            } catch (e: UnreadableFileException) {
                if (!walk.skipUnreadable) throw e
                emptyList()
            }
            for (child in children) {
                visit(child, Files.readAttributes(child, BasicFileAttributes::class.java, NOFOLLOW), walk)
            }
        } finally {
            if (walk.purpose == Purpose.READ) OwnerAccess.restore(dir, saved)
        }
        walk.visitor.leaveDirectory(dir)
    }

    private fun open(dir: Path, purpose: Purpose): Int? = when (purpose) {
        Purpose.READ -> OwnerAccess.openDirectory(dir)
        Purpose.DELETE -> OwnerAccess.openForDelete(dir).let { null }
    }

    private fun names(dir: Path): List<Path> = try {
        Files.newDirectoryStream(dir).use { stream -> stream.sortedBy { it.fileName.toString() } }
    } catch (e: IOException) {
        throw UnreadableFileException(dir, e)
    }
}
