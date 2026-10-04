// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.data.rootfs.ExtractorFixtures.sha256
import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** The stream of an OCI layer must feed its digest whatever way the reader goes through it. */
class OciLayerStreamTest {
    private val data = ByteArray(5_000) { (it * 7).toByte() }

    private fun layer(expected: String = sha256(data)) =
        OciArchive.Layer(BufferedInputStream(ByteArrayInputStream(data)), expected)

    @Test
    fun readingByteByByteAndInBlocksIsAllHashed() {
        val layer = layer()

        repeat(10) { layer.stream.read() }
        layer.stream.read(ByteArray(700), 0, 700)
        layer.verify()
    }

    @Test
    fun aSkipStillFeedsTheDigest() {
        val layer = layer()

        val skipped = layer.stream.skip(1_000)
        layer.verify()

        assertEquals(true, skipped > 0)
    }

    @Test
    fun skippingAtTheEndOrNothingMovesNowhere() {
        val layer = layer()
        layer.stream.read(ByteArray(data.size))

        assertEquals(0L, layer.stream.skip(10))
        assertEquals(0L, layer.stream.skip(0))
    }

    @Test
    fun markAndResetAreNotOfferedBecauseTheyWouldReplayBytes() {
        val stream = layer().stream

        assertFalse(stream.markSupported())
        stream.mark(10)
        assertThrows(IOException::class.java) { stream.reset() }
    }

    @Test
    fun closingTheStreamLeavesTheOuterArchiveOpen() {
        val layer = layer()

        layer.stream.close()

        layer.verify()
    }

    @Test
    fun aDigestThatDoesNotMatchIsCorruptAfterTheBytesAreDrained() {
        val failure = assertThrows(ExtractionFailure::class.java) { layer("f".repeat(64)).verify() }

        assertEquals(
            ExtractionError.Corrupt("the OCI layer does not match its digest"),
            failure.error
        )
    }
}
