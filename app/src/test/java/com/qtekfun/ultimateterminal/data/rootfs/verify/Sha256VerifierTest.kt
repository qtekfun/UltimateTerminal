// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs.verify

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class Sha256VerifierTest {
    @TempDir
    lateinit var dir: File

    private val verifier = Sha256Verifier()

    private fun fileOf(bytes: ByteArray) = File(dir, "archive").also { it.writeBytes(bytes) }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun matchingHashAndSizeAreVerified() {
        val bytes = "hello rootfs".toByteArray()
        assertEquals(
            Verification.Verified,
            verifier.verify(fileOf(bytes), sha256(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun theExpectedHashMayBeUpperCase() {
        val bytes = "hello rootfs".toByteArray()
        assertEquals(
            Verification.Verified,
            verifier.verify(fileOf(bytes), sha256(bytes).uppercase(), null)
        )
    }

    @Test
    fun anUnknownSizeIsNotChecked() {
        val bytes = ByteArray(10) { it.toByte() }
        assertEquals(Verification.Verified, verifier.verify(fileOf(bytes), sha256(bytes), null))
    }

    @Test
    fun aWrongSizeIsReportedBeforeHashing() {
        val bytes = ByteArray(10)
        assertEquals(
            Verification.SizeMismatch(expected = 11, actual = 10),
            verifier.verify(fileOf(bytes), sha256(bytes), 11)
        )
    }

    @Test
    fun aWrongHashIsReportedWithBothValues() {
        val bytes = "tampered".toByteArray()
        val other = sha256("original".toByteArray())
        assertEquals(
            Verification.HashMismatch(expected = other, actual = sha256(bytes)),
            verifier.verify(fileOf(bytes), other.uppercase(), bytes.size.toLong())
        )
    }

    @Test
    fun filesLargerThanTheReadBufferAreHashedWhole() {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        assertEquals(
            Verification.Verified,
            verifier.verify(fileOf(bytes), sha256(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun anEmptyFileHasTheHashOfNothing() {
        val empty = ByteArray(0)
        assertEquals(Verification.Verified, verifier.verify(fileOf(empty), sha256(empty), 0))
    }

    @Test
    fun aChecksumThatIsNotSha256HexIsMalformed() {
        val file = fileOf(ByteArray(1))
        for (bad in listOf("", "abc", "z".repeat(64), sha256(ByteArray(1)) + "0")) {
            assertEquals(Verification.MalformedChecksum(bad), verifier.verify(file, bad, null))
        }
    }

    @Test
    fun anUnreadableFileIsAnIoErrorForTheCallerToHandle() {
        assertThrows(IOException::class.java) {
            verifier.verify(File(dir, "missing"), "0".repeat(64), null)
        }
    }
}
