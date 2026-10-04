// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** The compression of a root filesystem archive, told apart by the first bytes of the file. */
internal enum class ArchiveFormat(val label: String, magicHex: String) {
    GZIP("gzip", "1f8b"),
    XZ("xz", "fd377a585a00"),
    BZIP2("bzip2", "425a68"),
    ZSTD("zstd", "28b52ffd"),

    /** A plain tar has no magic at the start; its header says "ustar" much further in. */
    PLAIN_TAR("tar", "");

    /** The leading bytes, written in hex as the format specifications give them. */
    private val magic: ByteArray =
        magicHex.chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray()

    fun matches(head: ByteArray, length: Int): Boolean =
        magic.isNotEmpty() && length >= magic.size && magic.indices.all { head[it] == magic[it] }

    companion object {
        /** The longest magic number, which is how many bytes have to be looked at. */
        private const val LOOKAHEAD = 6

        /**
         * Fills [buffer] from [input], or as much as there is. `InputStream.readNBytes` does this but
         * only exists from API 33 and the app supports 26, so it is done by hand; one `read` call is
         * allowed to return fewer bytes than asked without being at the end.
         */
        private fun readFully(input: InputStream, buffer: ByteArray): Int {
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            return total
        }

        fun detect(head: ByteArray, length: Int): ArchiveFormat =
            entries.firstOrNull { it.matches(head, length) } ?: PLAIN_TAR

        /**
         * A stream of the plain tar inside [input]. Only gzip and plain tar are read: the others
         * are named in the error so the user is told what was wrong with the file.
         */
        fun open(input: BufferedInputStream): Result {
            input.mark(LOOKAHEAD)
            val head = ByteArray(LOOKAHEAD)
            val read = readFully(input, head)
            input.reset()
            return when (val format = detect(head, read)) {
                GZIP -> Result.Opened(GZIPInputStream(input))
                PLAIN_TAR -> Result.Opened(input)
                else -> Result.Unsupported(ExtractionError.UnsupportedFormat(format.label))
            }
        }
    }

    sealed interface Result {
        data class Opened(val stream: InputStream) : Result

        data class Unsupported(val error: ExtractionError.UnsupportedFormat) : Result
    }
}
