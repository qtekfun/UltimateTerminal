// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupSource
import java.io.BufferedInputStream
import java.io.InputStream

/** Opens a backup file as the plain tar inside it, decrypting when it is encrypted. */
internal object BackupOpener {
    /** A plain backup starts with the name of its first entry. */
    private const val PLAIN_START = "manifest.json"

    suspend fun open(source: BackupSource, password: String?): InputStream {
        val input = BufferedInputStream(source.open())
        return if (isEncrypted(input)) decrypting(input, password) else input
    }

    /** Peeks at the start: the encrypted format starts with its magic, a plain tar with a name. */
    fun isEncrypted(input: BufferedInputStream): Boolean {
        input.mark(BackupCrypto.MAGIC.length)
        val head = ByteArray(BackupCrypto.MAGIC.length)
        val read = readFully(input, head)
        input.reset()
        val text = String(head, 0, read, Charsets.US_ASCII)
        return when {
            text == BackupCrypto.MAGIC -> true
            PLAIN_START.startsWith(text) && read == head.size -> false
            else -> throw BackupFailure(BackupError.NotABackup)
        }
    }

    private fun decrypting(input: InputStream, password: String?): InputStream {
        if (password.isNullOrEmpty()) throw BackupFailure(BackupError.PasswordRequired)
        val bytes = ByteArray(EncryptionHeader.SIZE)
        if (readFully(input, bytes) < bytes.size) throw BackupFailure(BackupError.Truncated)
        val header = EncryptionHeader.decode(bytes)
        val key = BackupCrypto.deriveKey(password, header.salt, header.iterations)
        return DecryptingInputStream(input, key, header)
    }
}

/** Fills [buffer] from [input], or as much as there is; one `read` may return fewer bytes. */
internal fun readFully(input: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val read = input.read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return total
}

/** `InputStream.readNBytes` only exists from API 33. */
internal fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    return buffer.copyOf(readFully(this, buffer))
}
