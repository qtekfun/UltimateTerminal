// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.math.BigInteger

/**
 * Reads a DER `SEQUENCE` made only of `INTEGER`s, which is all an RSA key in PKCS#1 is. Anything
 * else (a wrong tag, a length that overruns the bytes) gives null instead of an exception.
 */
internal class DerSequence private constructor(private val der: ByteArray) {
    private var position = 0

    private class BadDer : Exception()

    private fun next(): Int = (der.getOrNull(position++)?.toInt() ?: throw BadDer()) and BYTE_MASK

    private fun length(): Int {
        val first = next()
        if (first < LONG_FORM) return first
        val count = first - LONG_FORM
        if (count !in 1..LENGTH_BYTES) throw BadDer()
        var value = 0
        repeat(count) { value = (value shl BYTE_BITS) or next() }
        return value
    }

    /** Reads the tag and length of the outer SEQUENCE and returns where its content ends. */
    private fun sequenceEnd(): Int {
        if (next() != SEQUENCE) throw BadDer()
        val total = length()
        val end = position + total
        if (total < 0 || end > der.size) throw BadDer()
        return end
    }

    private fun integer(end: Int): BigInteger {
        if (next() != INTEGER) throw BadDer()
        val size = length()
        if (size < 0 || position + size > end) throw BadDer()
        return BigInteger(der.copyOfRange(position, position + size)).also { position += size }
    }

    private fun integers(): List<BigInteger> {
        val end = sequenceEnd()
        val out = mutableListOf<BigInteger>()
        while (position < end) out += integer(end)
        return out
    }

    companion object {
        private const val SEQUENCE = 0x30
        private const val INTEGER = 0x02
        private const val LONG_FORM = 0x80
        private const val LENGTH_BYTES = 4

        /** The integers of [der], or null if it is not a sequence of integers. */
        fun integersOf(der: ByteArray): List<BigInteger>? = try {
            DerSequence(der).integers()
        } catch (_: BadDer) {
            null
        }
    }
}
