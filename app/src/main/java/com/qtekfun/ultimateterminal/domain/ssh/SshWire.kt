// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.io.ByteArrayOutputStream
import java.math.BigInteger

internal const val INT_BYTES = 4
internal const val BYTE_BITS = 8
internal const val BYTE_MASK = 0xFF

/** A key file that does not parse; caught at the edge and turned into [SshError.CorruptKey]. */
internal class MalformedKey : Exception()

/** Appends SSH wire-format values (RFC 4251): uint32, string and mpint. */
internal class SshWriter {
    private val out = ByteArrayOutputStream()

    fun size(): Int = out.size()

    fun raw(data: ByteArray) = out.write(data)

    fun int(value: Int) {
        repeat(INT_BYTES) { index ->
            out.write((value ushr (BYTE_BITS * (INT_BYTES - 1 - index))) and BYTE_MASK)
        }
    }

    fun bytes(data: ByteArray) {
        int(data.size)
        raw(data)
    }

    fun string(value: String) = bytes(value.toByteArray(Charsets.UTF_8))

    fun mpint(value: BigInteger) =
        bytes(if (value.signum() == 0) ByteArray(0) else value.toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Reads SSH wire-format values; any overrun is a [MalformedKey], never another exception. */
internal class SshReader(private val data: ByteArray) {
    private var position = 0

    fun expect(prefix: ByteArray) {
        if (data.size < prefix.size || !data.copyOfRange(0, prefix.size).contentEquals(prefix)) {
            throw MalformedKey()
        }
        position = prefix.size
    }

    fun int(): Int {
        if (data.size - position < INT_BYTES) throw MalformedKey()
        var value = 0
        repeat(INT_BYTES) {
            value = (value shl BYTE_BITS) or (data[position++].toInt() and BYTE_MASK)
        }
        return value
    }

    fun bytes(): ByteArray {
        val length = int()
        if (length < 0 || length > data.size - position) throw MalformedKey()
        return data.copyOfRange(position, position + length).also { position += length }
    }

    fun text(): String = String(bytes(), Charsets.UTF_8)

    fun mpint(): BigInteger = bytes().let { if (it.isEmpty()) BigInteger.ZERO else BigInteger(it) }
}
