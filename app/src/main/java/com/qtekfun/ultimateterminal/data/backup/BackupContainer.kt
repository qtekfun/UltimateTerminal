// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream

/**
 * A backup is a plain tar archive (readable with any `tar`) holding `manifest.json`, `config.json`
 * and one gzip-compressed root filesystem per distro; encryption, when asked for, wraps the whole
 * thing.
 */
internal class ContainerWriter(out: OutputStream) : Closeable {
    private val tar = TarArchiveOutputStream(out).apply {
        setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR)
    }

    fun bytes(name: String, data: ByteArray) {
        tar.putArchiveEntry(TarArchiveEntry(name).also { it.size = data.size.toLong() })
        tar.write(data)
        tar.closeArchiveEntry()
    }

    /** Copies exactly [size] bytes of [input] as the part [name]. */
    fun stream(name: String, size: Long, input: InputStream) {
        tar.putArchiveEntry(TarArchiveEntry(name).also { it.size = size })
        input.copyTo(tar)
        tar.closeArchiveEntry()
    }

    override fun close() = tar.close()
}

internal class ContainerEntry(val name: String, val size: Long, val content: InputStream)

/** Reads the parts of a backup in order; refuses anything this app would not have written. */
internal class ContainerReader(input: InputStream) : Closeable {
    private val tar = TarArchiveInputStream(input)

    /** The next part, or null at the end. Its [ContainerEntry.content] is valid until the next call. */
    fun next(): ContainerEntry? {
        val entry = tar.nextEntry
        return when {
            entry == null -> null

            !entry.isFile || !PartNames.isKnown(entry.name) ->
                throw BackupFailure(BackupError.NotABackup)

            else -> ContainerEntry(entry.name, entry.size, PartStream(tar))
        }
    }

    override fun close() = tar.close()

    /** The bytes of one part; closing it must not close the whole archive. */
    private class PartStream(tar: InputStream) : FilterInputStream(tar) {
        override fun close() = Unit
    }
}
