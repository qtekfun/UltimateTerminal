// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

private const val SHA_256 = "SHA-256"
private const val DRAIN_BUFFER = 64 * 1024

private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

/** Reads [input] while hashing and counting what passes, to check a part against the manifest. */
internal class DigestingInputStream(private val input: InputStream) : InputStream() {
    private val digest = MessageDigest.getInstance(SHA_256)
    var bytes: Long = 0
        private set

    override fun read(): Int = input.read().also {
        if (it >= 0) {
            digest.update(it.toByte())
            bytes++
        }
    }

    override fun read(data: ByteArray, off: Int, len: Int): Int = input.read(data, off, len).also {
        if (it > 0) {
            digest.update(data, off, it)
            bytes += it
        }
    }

    /** Reads what is left, so the hash covers the whole part even if a reader stopped early. */
    fun drain() {
        val buffer = ByteArray(DRAIN_BUFFER)
        while (read(buffer, 0, buffer.size) >= 0) {
            // only the side effect on the hash and the count matters
        }
    }

    /** The SHA-256 of what was read so far, as 64 lowercase hex digits. */
    fun sha256(): String = digest.hex()
}

/** Writes to [out] while hashing and counting, to know a part's hash and size without a second read. */
internal class DigestingOutputStream(private val out: OutputStream) : OutputStream() {
    private val digest = MessageDigest.getInstance(SHA_256)
    var bytes: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        digest.update(b.toByte())
        bytes++
    }

    override fun write(data: ByteArray, off: Int, len: Int) {
        out.write(data, off, len)
        digest.update(data, off, len)
        bytes += len
    }

    override fun flush() = out.flush()

    override fun close() = out.close()

    fun sha256(): String = digest.hex()
}
