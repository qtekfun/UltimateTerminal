// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

/**
 * The root filesystem inside an OCI image archive, which is how Fedora publishes its base image: a
 * tar with `oci-layout`, `index.json` and `blobs/sha256/<digest>`, one blob per layer, config and
 * manifest. The layer is itself a (gzip) tar of the root filesystem.
 *
 * The archive is read once, front to back, and the manifest comes last, so the layer cannot be
 * looked up from it. It is found by its content instead: the one blob that is a compressed or plain
 * tar and not JSON. Blobs are named after their own SHA-256, which is what checks the layer here,
 * on top of the hash of the whole file that the download already verified.
 */
internal object OciArchive {
    private const val PEEK_BYTES = 512
    private const val USTAR_OFFSET = 257
    private const val DRAIN_BUFFER = 64 * 1024
    private const val MAX_LAYER_BYTES = 8L * 1024 * 1024 * 1024
    private val USTAR = "ustar".toByteArray()
    private val BLOB_NAME = Regex("""(?:\./)?blobs/sha256/([0-9a-f]{64})""")
    private val OCI_ENTRIES = setOf("oci-layout", "index.json", "blobs", "blobs/", "blobs/sha256/")

    /** Whether an archive whose first entry is [firstEntryName] is an OCI image and not a rootfs. */
    fun isOci(firstEntryName: String): Boolean {
        val name = firstEntryName.removePrefix("./")
        return name in OCI_ENTRIES || name.startsWith("blobs/sha256/")
    }

    /** The layer found: its bytes, hashed as they are read, and the digest its name promises. */
    class Layer(source: BufferedInputStream, private val expectedSha256: String) {
        private val digest = MessageDigest.getInstance("SHA-256")

        /** Every byte goes through [read], so the digest sees them whatever the reader does. */
        val stream: InputStream = object : FilterInputStream(source) {
            override fun read(): Int = super.read().also { if (it >= 0) digest.update(it.toByte()) }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also {
                    if (it >
                        0
                    ) {
                        digest.update(buffer, offset, it)
                    }
                }

            // A skip would go around the digest, and a reset would replay bytes it already has.
            override fun skip(count: Long): Long {
                val sink = ByteArray(minOf(count, DRAIN_BUFFER.toLong()).toInt().coerceAtLeast(1))
                val read = read(sink)
                return if (read < 0) 0L else read.toLong()
            }

            override fun markSupported(): Boolean = false

            override fun mark(readlimit: Int) = Unit

            override fun reset(): Unit = throw IOException("mark/reset not supported")

            // The outer archive goes on after the layer: only the caller ends it.
            override fun close() = Unit
        }

        /** Reads what the decompressor left unread, then checks the digest against the blob's name. */
        fun verify() {
            val sink = ByteArray(DRAIN_BUFFER)
            while (stream.read(sink) >= 0) {
                // Only the digest cares about these bytes.
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expectedSha256) {
                throw ExtractionFailure(
                    ExtractionError.Corrupt("the OCI layer does not match its digest")
                )
            }
        }
    }

    /** The layer, starting from [first], the entry already read. A layer must be the only one. */
    fun findLayer(outer: TarArchiveInputStream, first: TarArchiveEntry): Layer {
        var entry: TarArchiveEntry? = first
        var layer: Layer? = null
        while (entry != null && layer == null) {
            layer = layerOf(outer, entry)
            if (layer == null) entry = outer.nextEntry
        }
        return layer
            ?: throw ExtractionFailure(ExtractionError.Corrupt("the OCI archive has no layer"))
    }

    /** After the layer: another one would mean a rootfs made of several, which is not unpacked. */
    fun requireNoOtherLayer(outer: TarArchiveInputStream) {
        var entry = outer.nextEntry
        while (entry != null) {
            if (layerOf(outer, entry) != null) {
                throw ExtractionFailure(
                    ExtractionError.UnsupportedFormat("an OCI image with several layers")
                )
            }
            entry = outer.nextEntry
        }
    }

    private fun layerOf(outer: TarArchiveInputStream, entry: TarArchiveEntry): Layer? {
        val digest = BLOB_NAME.matchEntire(entry.name)?.groupValues?.get(1)
        if (entry.size > MAX_LAYER_BYTES) {
            throw ExtractionFailure(ExtractionError.TooLarge("an OCI layer of ${entry.size} bytes"))
        }
        val buffered = BufferedInputStream(outer, PEEK_BYTES)
        return if (entry.isFile && digest != null && looksLikeLayer(buffered)) {
            Layer(buffered, digest)
        } else {
            null
        }
    }

    /** A layer is a compressed or plain tar; the config and the manifest are JSON. */
    private fun looksLikeLayer(input: BufferedInputStream): Boolean {
        input.mark(PEEK_BYTES)
        val head = ByteArray(PEEK_BYTES)
        var total = 0
        while (total < head.size) {
            val read = input.read(head, total, head.size - total)
            if (read < 0) break
            total += read
        }
        input.reset()
        val format = ArchiveFormat.detect(head, total)
        return format != ArchiveFormat.PLAIN_TAR || hasUstar(head, total)
    }

    private fun hasUstar(head: ByteArray, length: Int): Boolean =
        length >= USTAR_OFFSET + USTAR.size &&
            USTAR.indices.all { head[USTAR_OFFSET + it] == USTAR[it] }
}
