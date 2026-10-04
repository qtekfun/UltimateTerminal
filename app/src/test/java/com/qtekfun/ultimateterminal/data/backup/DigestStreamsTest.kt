// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DigestStreamsTest {
    private val data = ByteArray(100_000) { (it % 251).toByte() }

    @Test
    fun readingAThingHashesAndCountsIt() {
        val stream = DigestingInputStream(ByteArrayInputStream(data))
        assertEquals(data[0].toInt() and 0xFF, stream.read())
        val rest = ByteArray(10)
        assertEquals(10, stream.read(rest, 0, 10))
        stream.drain()
        assertEquals(-1, stream.read())
        assertEquals(data.size.toLong(), stream.bytes)
        assertEquals(sha256Hex(data), stream.sha256())
    }

    @Test
    fun anEmptyStreamHasTheHashOfNothing() {
        val stream = DigestingInputStream(ByteArrayInputStream(ByteArray(0)))
        assertEquals(-1, stream.read(ByteArray(4), 0, 4))
        assertEquals(0, stream.bytes)
        assertEquals(sha256Hex(ByteArray(0)), stream.sha256())
    }

    @Test
    fun writingAThingHashesAndCountsIt() {
        val sink = ByteArrayOutputStream()
        val stream = DigestingOutputStream(sink)
        stream.write(data[0].toInt())
        stream.write(data, 1, data.size - 1)
        stream.flush()
        stream.close()
        assertArrayEquals(data, sink.toByteArray())
        assertEquals(data.size.toLong(), stream.bytes)
        assertEquals(sha256Hex(data), stream.sha256())
    }

    @Test
    fun countingStreamCountsWhatPassesThrough() {
        val sink = ByteArrayOutputStream()
        val stream = CountingOutputStream(sink)
        stream.write(7)
        stream.write(ByteArray(9), 0, 9)
        stream.flush()
        stream.close()
        assertEquals(10, stream.bytes)
        assertEquals(10, sink.size())
    }
}
