// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupCryptoTest {
    private val chunk = 16
    private val key = BackupCrypto.deriveKey("correct horse", ByteArray(16) { 1 }, 1_000)
    private val header = EncryptionHeader(1_000, ByteArray(16) { 1 }, ByteArray(7) { 2 }, chunk)

    private fun seal(data: ByteArray, header: EncryptionHeader = this.header): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptingOutputStream(out, key, header).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(
        sealed: ByteArray,
        key: SecretKeySpec = this.key,
        header: EncryptionHeader = this.header
    ): ByteArray {
        val input = ByteArrayInputStream(sealed, EncryptionHeader.SIZE, sealed.size)
        return DecryptingInputStream(input, key, header).readBytes()
    }

    private fun failure(block: () -> Unit): BackupError = assertThrows<BackupFailure>(block).error

    private fun data(size: Int) = ByteArray(size) { (it * 7).toByte() }

    @Test
    fun dataOfAnySizeComesBackIdentical() {
        // empty, less than a chunk, exactly one, one more, and many chunks
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, chunk * 5, chunk * 5 + 3)) {
            assertArrayEquals(data(size), open(seal(data(size))), "size $size")
        }
    }

    @Test
    fun bytesWrittenOneAtATimeAreReadOneAtATime() {
        val out = ByteArrayOutputStream()
        EncryptingOutputStream(out, key, header).use { stream ->
            data(40).forEach { stream.write(it.toInt()) }
        }
        val reader = DecryptingInputStream(
            ByteArrayInputStream(out.toByteArray(), EncryptionHeader.SIZE, out.size()),
            key,
            header
        )
        val read = ByteArray(40) { reader.read().toByte() }
        assertArrayEquals(data(40), read)
        assertEquals(-1, reader.read())
        assertEquals(-1, reader.read(ByteArray(4), 0, 4))
    }

    @Test
    fun closingTwiceSealsOnlyOnce() {
        val out = ByteArrayOutputStream()
        val stream = EncryptingOutputStream(out, key, header)
        stream.write(data(5))
        stream.close()
        val size = out.size()
        stream.close()
        assertEquals(size, out.size())
    }

    @Test
    fun theHeaderComesFirstAndIsReadable() {
        val sealed = seal(data(3))
        val read = EncryptionHeader.decode(sealed.copyOf(EncryptionHeader.SIZE))
        assertEquals(1_000, read.iterations)
        assertArrayEquals(header.salt, read.salt)
        assertArrayEquals(header.noncePrefix, read.noncePrefix)
        assertEquals(chunk, read.chunkBytes)
        assertEquals(
            BackupCrypto.MAGIC,
            String(sealed, 0, BackupCrypto.MAGIC.length, Charsets.US_ASCII)
        )
    }

    @Test
    fun aWrongPasswordIsRefused() {
        val wrong = BackupCrypto.deriveKey("battery staple", header.salt, header.iterations)
        assertEquals(
            BackupError.WrongPasswordOrCorrupt,
            failure { open(seal(data(50)), key = wrong) }
        )
    }

    @Test
    fun aChangedByteAnywhereIsDetected() {
        val sealed = seal(data(50))
        for (position in EncryptionHeader.SIZE until sealed.size step 5) {
            val changed = sealed.copyOf().also { it[position] = (it[position] + 1).toByte() }
            assertEquals(
                BackupError.WrongPasswordOrCorrupt,
                failure {
                    open(changed)
                },
                "at $position"
            )
        }
    }

    @Test
    fun aChangedHeaderFieldIsDetectedBecauseTheHeaderIsAuthenticated() {
        val sealed = seal(data(50))
        val otherPrefix = EncryptionHeader(1_000, header.salt, ByteArray(7) { 9 }, chunk)
        assertEquals(
            BackupError.WrongPasswordOrCorrupt,
            failure {
                open(sealed, header = otherPrefix)
            }
        )
    }

    @Test
    fun aFileCutAtAChunkBoundaryIsDetected() {
        val sealed = seal(data(chunk * 3))
        val sealedChunk = chunk + BackupCrypto.TAG_BYTES
        // two whole chunks and nothing else: the second was not the last one when it was sealed
        val cut = sealed.copyOf(EncryptionHeader.SIZE + 2 * sealedChunk)
        assertEquals(BackupError.WrongPasswordOrCorrupt, failure { open(cut) })
    }

    @Test
    fun aFileCutInTheMiddleOfAChunkIsDetected() {
        val sealed = seal(data(chunk * 3))
        val cut = sealed.copyOf(sealed.size - 4)
        assertEquals(BackupError.WrongPasswordOrCorrupt, failure { open(cut) })
    }

    @Test
    fun aFileWithoutAnyChunkIsTruncated() {
        val sealed = seal(data(10)).copyOf(EncryptionHeader.SIZE)
        assertEquals(BackupError.Truncated, failure { open(sealed) })
        // a few bytes, less than one tag
        val stub = seal(data(10)).copyOf(EncryptionHeader.SIZE + 3)
        assertEquals(BackupError.Truncated, failure { open(stub) })
    }

    @Test
    fun reorderedOrRepeatedChunksAreDetected() {
        val sealed = seal(data(chunk * 3))
        val size = chunk + BackupCrypto.TAG_BYTES
        val head = sealed.copyOf(EncryptionHeader.SIZE)
        fun chunkAt(n: Int) = sealed.copyOfRange(
            EncryptionHeader.SIZE + n * size,
            EncryptionHeader.SIZE + (n + 1) * size
        )
        val last = sealed.copyOfRange(EncryptionHeader.SIZE + 3 * size, sealed.size)
        val swapped = head + chunkAt(1) + chunkAt(0) + chunkAt(2) + last
        val repeated = head + chunkAt(0) + chunkAt(0) + chunkAt(2) + last
        assertEquals(BackupError.WrongPasswordOrCorrupt, failure { open(swapped) })
        assertEquals(BackupError.WrongPasswordOrCorrupt, failure { open(repeated) })
    }

    @Test
    fun theSameDataSealedTwiceUnderTheSameKeyIsNotTheSameBytesWhenThePrefixDiffers() {
        val other = EncryptionHeader(1_000, header.salt, ByteArray(7) { 5 }, chunk)
        assertNotEquals(seal(data(20)).toList(), seal(data(20), other).toList())
    }

    @Test
    fun aHeaderWithAbsurdLimitsIsNotABackup() {
        fun encoded(iterations: Int, chunkBytes: Int) =
            EncryptionHeader(iterations, header.salt, header.noncePrefix, chunkBytes).encode()
        for ((iterations, chunkBytes) in listOf(
            BackupCrypto.MIN_ITERATIONS - 1 to chunk,
            BackupCrypto.MAX_ITERATIONS + 1 to chunk,
            1_000 to 0,
            1_000 to BackupCrypto.MAX_CHUNK_BYTES + 1
        )) {
            assertEquals(
                BackupError.NotABackup,
                failure {
                    EncryptionHeader.decode(encoded(iterations, chunkBytes))
                }
            )
        }
        assertTrue(EncryptionHeader.decode(encoded(BackupCrypto.MIN_ITERATIONS, 1)).chunkBytes == 1)
    }

    @Test
    fun theSameDerivationGivesTheSameKeyAndAnotherPasswordAnotherOne() {
        val again = BackupCrypto.deriveKey("correct horse", header.salt, 1_000)
        assertArrayEquals(key.encoded, again.encoded)
        assertNotEquals(
            key.encoded.toList(),
            BackupCrypto.deriveKey("correct horsf", header.salt, 1_000).encoded.toList()
        )
        assertEquals(32, key.encoded.size)
    }
}
