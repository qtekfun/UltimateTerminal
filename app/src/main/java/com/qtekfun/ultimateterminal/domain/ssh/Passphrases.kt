// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.nio.CharBuffer

/** Passphrases as the bytes OpenSSH derives its key from: the UTF-8 text, nothing else. */
object Passphrases {
    /** The shortest passphrase the app accepts when one is set: a four-digit PIN is the floor. */
    const val MIN_LENGTH = 4

    /** How a new passphrase and its confirmation fare. An empty pair means "no passphrase". */
    enum class Check { OK, TOO_SHORT, MISMATCH }

    fun check(passphrase: CharSequence, confirmation: CharSequence): Check = when {
        passphrase.isEmpty() && confirmation.isEmpty() -> Check.OK
        passphrase.length < MIN_LENGTH -> Check.TOO_SHORT
        passphrase.toString() != confirmation.toString() -> Check.MISMATCH
        else -> Check.OK
    }

    /** UTF-8 bytes of [chars]; the caller wipes the result. [chars] itself is left untouched. */
    fun toBytes(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}
