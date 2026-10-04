// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encryption of a backup (SPEC RF-06): AES-256-GCM over chunks, with the key derived from the
 * user's password by PBKDF2-HMAC-SHA256.
 *
 * Layout: a header, then chunks of [EncryptionHeader.chunkBytes] bytes of data each sealed with its
 * own 16-byte tag, so a backup of any size is read and written in a small buffer. The nonce of a
 * chunk is the header's random prefix, the chunk number and a flag for the last chunk; the whole
 * header is authenticated with every chunk. That makes a reordered, repeated, dropped or
 * truncated chunk fail authentication, like a changed byte or a wrong password.
 */
internal object BackupCrypto {
    const val MAGIC = "UTBKENC1"
    const val DEFAULT_ITERATIONS = 600_000
    const val DEFAULT_CHUNK_BYTES = 64 * 1024
    const val MIN_ITERATIONS = 1_000
    const val MAX_ITERATIONS = 10_000_000
    const val MAX_CHUNK_BYTES = 1024 * 1024
    const val SALT_BYTES = 16
    const val NONCE_PREFIX_BYTES = 7
    const val TAG_BYTES = 16
    private const val KEY_BITS = 256
    private const val BITS_PER_BYTE = 8
    private const val TAG_BITS = TAG_BYTES * BITS_PER_BYTE
    private const val NONCE_BYTES = 12

    fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** The 12-byte nonce of chunk [counter]; the last chunk is told apart by its final byte. */
    fun nonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(NONCE_BYTES).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    fun cipher(mode: Int, key: SecretKeySpec, header: EncryptionHeader, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header.encode())
        }
}

internal class EncryptionHeader(
    val iterations: Int,
    val salt: ByteArray,
    val noncePrefix: ByteArray,
    val chunkBytes: Int
) {
    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(BackupCrypto.MAGIC.toByteArray(Charsets.US_ASCII))
        .putInt(iterations)
        .put(salt)
        .put(noncePrefix)
        .putInt(chunkBytes)
        .array()

    companion object {
        const val SIZE = 8 + 4 + BackupCrypto.SALT_BYTES + BackupCrypto.NONCE_PREFIX_BYTES + 4

        /** Reads a header whose [bytes] are all there; the limits keep a hostile file cheap to refuse. */
        fun decode(bytes: ByteArray): EncryptionHeader {
            val buffer = ByteBuffer.wrap(bytes)
            buffer.position(BackupCrypto.MAGIC.length)
            val iterations = buffer.getInt()
            val salt = ByteArray(BackupCrypto.SALT_BYTES).also { buffer.get(it) }
            val prefix = ByteArray(BackupCrypto.NONCE_PREFIX_BYTES).also { buffer.get(it) }
            val chunk = buffer.getInt()
            val sane = iterations in BackupCrypto.MIN_ITERATIONS..BackupCrypto.MAX_ITERATIONS &&
                chunk in 1..BackupCrypto.MAX_CHUNK_BYTES
            if (!sane) throw BackupFailure(BackupError.NotABackup)
            return EncryptionHeader(iterations, salt, prefix, chunk)
        }
    }
}

/** Writes [header] and then everything given to it, sealed chunk by chunk. */
internal class EncryptingOutputStream(
    private val out: OutputStream,
    private val key: SecretKeySpec,
    private val header: EncryptionHeader
) : OutputStream() {
    private val buffer = ByteArray(header.chunkBytes)
    private var filled = 0
    private var counter = 0
    private var closed = false

    init {
        out.write(header.encode())
    }

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(data: ByteArray, off: Int, len: Int) {
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full chunk is only sealed when more data follows, so the last one is known at close.
            if (filled == buffer.size) seal(last = false)
            val count = minOf(remaining, buffer.size - filled)
            System.arraycopy(data, offset, buffer, filled, count)
            filled += count
            offset += count
            remaining -= count
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            seal(last = true)
            out.close()
        }
    }

    private fun seal(last: Boolean) {
        val nonce = BackupCrypto.nonce(header.noncePrefix, counter, last)
        val cipher = BackupCrypto.cipher(Cipher.ENCRYPT_MODE, key, header, nonce)
        out.write(cipher.doFinal(buffer, 0, filled))
        filled = 0
        counter++
    }
}

/** Reads what [EncryptingOutputStream] wrote; any change to the file makes a read throw. */
internal class DecryptingInputStream(
    input: InputStream,
    private val key: SecretKeySpec,
    private val header: EncryptionHeader
) : InputStream() {
    private val source = PushbackInputStream(input, 1)
    private val sealed = ByteArray(header.chunkBytes + BackupCrypto.TAG_BYTES)
    private var plain = ByteArray(0)
    private var position = 0
    private var counter = 0
    private var finished = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and BYTE_MASK
    }

    override fun read(data: ByteArray, off: Int, len: Int): Int {
        while (position == plain.size) {
            if (!nextChunk()) return -1
        }
        val count = minOf(len, plain.size - position)
        System.arraycopy(plain, position, data, off, count)
        position += count
        return count
    }

    override fun close() = source.close()

    private fun nextChunk(): Boolean {
        if (finished) return false
        val size = readFully()
        if (size < BackupCrypto.TAG_BYTES) throw BackupFailure(BackupError.Truncated)
        val last = size < sealed.size || atEnd()
        val nonce = BackupCrypto.nonce(header.noncePrefix, counter, last)
        val cipher = BackupCrypto.cipher(Cipher.DECRYPT_MODE, key, header, nonce)
        plain = try {
            cipher.doFinal(sealed, 0, size)
        } catch (_: AEADBadTagException) {
            throw BackupFailure(BackupError.WrongPasswordOrCorrupt)
        }
        position = 0
        counter++
        finished = last
        return true
    }

    private fun readFully(): Int {
        var total = 0
        while (total < sealed.size) {
            val read = source.read(sealed, total, sealed.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun atEnd(): Boolean {
        val next = source.read()
        if (next >= 0) source.unread(next)
        return next < 0
    }

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
