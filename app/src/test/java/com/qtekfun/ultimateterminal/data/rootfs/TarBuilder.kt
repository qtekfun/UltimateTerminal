// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

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
        add(
            TarArchiveEntry(name.trimEnd('/') + "/", true).also {
                it.mode = mode
                it.modTime =
                    FIXED_TIME
            }
        )
        tar.closeArchiveEntry()
    }

    fun file(name: String, content: String = "", mode: Int = 0b110_100_100): TarBuilder =
        fileBytes(name, content.toByteArray(), mode)

    /** A file whose content is not text, such as a compressed layer inside an OCI archive. */
    fun fileBytes(name: String, data: ByteArray, mode: Int = 0b110_100_100): TarBuilder = apply {
        val entry = TarArchiveEntry(name, true).also {
            it.size = data.size.toLong()
            it.mode = mode
            it.modTime = FIXED_TIME
        }
        add(entry)
        tar.write(data)
        tar.closeArchiveEntry()
    }

    fun symlink(name: String, target: String): TarBuilder = apply {
        add(
            TarArchiveEntry(name, TarConstants.LF_SYMLINK, true).also {
                it.linkName = target
                it.modTime =
                    FIXED_TIME
            }
        )
        tar.closeArchiveEntry()
    }

    fun hardLink(name: String, target: String): TarBuilder = apply {
        add(
            TarArchiveEntry(name, TarConstants.LF_LINK, true).also {
                it.linkName = target
                it.modTime =
                    FIXED_TIME
            }
        )
        tar.closeArchiveEntry()
    }

    fun device(name: String): TarBuilder = apply {
        add(TarArchiveEntry(name, TarConstants.LF_CHR, true).also { it.modTime = FIXED_TIME })
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

    /** The tar compressed with xz, the way Fedora publishes its image. */
    fun xz(): ByteArray = xzed(tar())

    /** Like [gzip], without compression; see [gzippedStored]. */
    fun gzipStored(): ByteArray = gzippedStored(tar())

    companion object {
        const val MODIFIED_MILLIS = 1_700_000_000_000L

        /**
         * Every entry carries this time, never the clock: an archive built a second later must be
         * the same bytes, or its compressed size, and so which branch a restore test reaches,
         * changes from run to run (it left a critical branch uncovered in two runs of six).
         */
        private val FIXED_TIME = Date(MODIFIED_MILLIS)
        private val PAX_COMMENT = "16 comment=test\n".toByteArray()
        private const val XZ_PRESET = 1
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

        /** xz at preset 1, whose 1 MiB dictionary is small enough to build in a test. */
        fun xzed(data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            XZOutputStream(out, LZMA2Options(XZ_PRESET)).use { it.write(data) }
            return out.toByteArray()
        }

        /**
         * A gzip that does not compress: its length depends only on the length of [data], so two
         * archives with different content of the same length are the same size, which no
         * compressing level can promise.
         */
        fun gzippedStored(data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            val stream = object : GZIPOutputStream(out) {
                init {
                    def.setLevel(Deflater.NO_COMPRESSION)
                }
            }
            stream.use { it.write(data) }
            return out.toByteArray()
        }
    }
}
