// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants

/**
 * Builds small synthetic tar archives for the extractor tests. Names are written exactly as given
 * (including `..` and absolute ones), because the point is to feed the extractor hostile input.
 */
class TarBuilder {
    private val bytes = ByteArrayOutputStream()
    private val tar = TarArchiveOutputStream(bytes).apply {
        setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    }

    fun dir(name: String, mode: Int = 0b111_101_101): TarBuilder = apply {
        add(TarArchiveEntry(name.trimEnd('/') + "/", true).also { it.mode = mode })
        tar.closeArchiveEntry()
    }

    fun file(name: String, content: String = "", mode: Int = 0b110_100_100): TarBuilder = apply {
        val data = content.toByteArray()
        val entry = TarArchiveEntry(name, true).also {
            it.size = data.size.toLong()
            it.mode = mode
            it.modTime = Date(MODIFIED_MILLIS)
        }
        add(entry)
        tar.write(data)
        tar.closeArchiveEntry()
    }

    fun symlink(name: String, target: String): TarBuilder = apply {
        add(TarArchiveEntry(name, TarConstants.LF_SYMLINK, true).also { it.linkName = target })
        tar.closeArchiveEntry()
    }

    fun hardLink(name: String, target: String): TarBuilder = apply {
        add(TarArchiveEntry(name, TarConstants.LF_LINK, true).also { it.linkName = target })
        tar.closeArchiveEntry()
    }

    fun device(name: String): TarBuilder = apply {
        add(TarArchiveEntry(name, TarConstants.LF_CHR, true))
        tar.closeArchiveEntry()
    }

    /**
     * A global PAX header, which `git archive` and many tools put first; it carries no file. The
     * library cannot write one as an entry, so its 512-byte header and data block are written
     * by hand (type `g`, name `pax_global_header`).
     */
    fun globalPaxHeader(): TarBuilder = apply {
        val header = ByteArray(TAR_BLOCK)
        "pax_global_header".toByteArray().copyInto(header)
        octal(header, MODE_OFFSET, MODE_LENGTH, 0b110_100_100L)
        octal(header, SIZE_OFFSET, SIZE_LENGTH, PAX_COMMENT.size.toLong())
        header[TYPE_OFFSET] = 'g'.code.toByte()
        "ustar".toByteArray().copyInto(header, MAGIC_OFFSET)
        "00".toByteArray().copyInto(header, MAGIC_OFFSET + MAGIC_LENGTH)
        " ".repeat(CHECKSUM_LENGTH).toByteArray().copyInto(header, CHECKSUM_OFFSET)
        octal(
            header,
            CHECKSUM_OFFSET,
            CHECKSUM_LENGTH - 1,
            header.sumOf {
                it.toInt() and 0xff
            }.toLong()
        )
        bytes.write(header)
        bytes.write(PAX_COMMENT.copyOf(roundUp(PAX_COMMENT.size)))
    }

    private fun octal(into: ByteArray, offset: Int, length: Int, value: Long) {
        val text = value.toString(radix = 8).padStart(length - 1, '0')
        text.toByteArray().copyInto(into, offset)
    }

    private fun roundUp(size: Int) = (size + TAR_BLOCK - 1) / TAR_BLOCK * TAR_BLOCK

    private fun add(entry: TarArchiveEntry) = tar.putArchiveEntry(entry)

    /** The plain tar bytes. */
    fun tar(): ByteArray {
        tar.finish()
        tar.close()
        return bytes.toByteArray()
    }

    fun gzip(): ByteArray = gzipped(tar())

    companion object {
        const val MODIFIED_MILLIS = 1_700_000_000_000L
        private val PAX_COMMENT = "16 comment=test\n".toByteArray()
        private const val TAR_BLOCK = 512
        private const val MODE_OFFSET = 100
        private const val MODE_LENGTH = 8
        private const val SIZE_OFFSET = 124
        private const val SIZE_LENGTH = 12
        private const val CHECKSUM_OFFSET = 148
        private const val CHECKSUM_LENGTH = 8
        private const val TYPE_OFFSET = 156
        private const val MAGIC_OFFSET = 257
        private const val MAGIC_LENGTH = 6

        fun gzipped(data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(data) }
            return out.toByteArray()
        }
    }
}
