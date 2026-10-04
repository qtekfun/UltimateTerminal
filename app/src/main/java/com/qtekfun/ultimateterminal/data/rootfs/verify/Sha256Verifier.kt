// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs.verify

import java.io.File
import java.security.MessageDigest

/** Outcome of checking a downloaded archive against what the official index published. */
sealed interface Verification {
    data object Verified : Verification

    data class SizeMismatch(val expected: Long, val actual: Long) : Verification

    data class HashMismatch(val expected: String, val actual: String) : Verification

    /** [value] is not a 64-digit hexadecimal SHA-256. */
    data class MalformedChecksum(val value: String) : Verification
}

/** Checks the size (when known) and the SHA-256 of a file. Reads the file in a stream. */
class Sha256Verifier {
    /**
     * @throws java.io.IOException if [file] cannot be read; the caller decides what that means.
     */
    fun verify(file: File, expectedSha256: String, expectedSize: Long?): Verification = when {
        !HEX_SHA256.matches(expectedSha256) -> Verification.MalformedChecksum(expectedSha256)

        expectedSize != null && file.length() != expectedSize ->
            Verification.SizeMismatch(expectedSize, file.length())

        else -> compareHash(file, expectedSha256.lowercase())
    }

    private fun compareHash(file: File, expected: String): Verification {
        val actual = sha256Of(file)
        return if (actual ==
            expected
        ) {
            Verification.Verified
        } else {
            Verification.HashMismatch(expected, actual)
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private val HEX_SHA256 = Regex("[0-9a-fA-F]{64}")
private const val BUFFER_SIZE = 64 * 1024
