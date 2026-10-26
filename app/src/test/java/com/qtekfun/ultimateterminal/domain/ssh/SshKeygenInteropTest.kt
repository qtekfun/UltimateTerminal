// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Both directions against the real OpenSSH tools, when `ssh-keygen` is installed (it is on the CI
 * runner and on a developer machine): keys made with `-N` open here, and keys written here open
 * with
 * `ssh-keygen -y`. The keys are made and deleted by the test; none is kept in the repository.
 */
class SshKeygenInteropTest {
    private lateinit var dir: File

    @BeforeEach
    fun setUp() {
        assumeTrue(run(null, "ssh-keygen", "-?").exit != EXEC_FAILED, "ssh-keygen is not installed")
        dir = Files.createTempDirectory(
            "ut-keygen",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        ).toFile()
    }

    @AfterEach
    fun tearDown() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private class Run(val exit: Int, val out: String)

    private fun run(workDir: File?, vararg command: String): Run = try {
        val process = ProcessBuilder(*command).directory(workDir).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        Run(process.exitValue(), out)
    } catch (_: java.io.IOException) {
        Run(EXEC_FAILED, "")
    }

    private fun keygen(vararg args: String) = run(dir, "ssh-keygen", "-q", *args)

    private fun publicLine(text: String, comment: String = "") =
        text.trim().split(" ").take(2).joinToString(" ") + comment

    @Test
    fun keysMadeWithEachCipherOpenHere() {
        for (cipher in listOf(
            "aes128-ctr",
            "aes192-ctr",
            "aes256-ctr",
            "aes128-cbc",
            "aes256-cbc"
        )) {
            val file = File(dir, "k_$cipher")
            assertEquals(
                0,
                keygen(
                    "-t", "ed25519", "-N", "pässphrase 1", "-a", "3", "-Z", cipher, "-f", file.path
                ).exit
            )
            val key = (
                OpenSshKeyFormat.parsePrivate(file.readText(), "pässphrase 1".toByteArray())
                    as SshResult.Success
                ).value
            assertEquals(
                publicLine(File(file.path + ".pub").readText()),
                publicLine(OpenSshKeyFormat.publicLine(key, "")),
                cipher
            )
        }
    }

    @Test
    fun anRsaKeyMadeWithAPassphraseOpensHere() {
        val file = File(dir, "k_rsa")
        assertEquals(0, keygen("-t", "rsa", "-b", "2048", "-N", "12345", "-f", file.path).exit)
        val key = (
            OpenSshKeyFormat.parsePrivate(file.readText(), "12345".toByteArray())
                as SshResult.Success
            ).value
        assertEquals(
            publicLine(File(file.path + ".pub").readText()),
            publicLine(OpenSshKeyFormat.publicLine(key, ""))
        )
        val fingerprint = run(dir, "ssh-keygen", "-lf", file.path + ".pub").out
        assertTrue(fingerprint.contains(OpenSshKeyFormat.fingerprint(key)))
    }

    @Test
    fun aKeyProtectedHereOpensWithSshKeygen() {
        // A real pair is needed for ssh-keygen to accept it: ask ssh-keygen for one.
        val seedFile = File(dir, "seed")
        assertEquals(0, keygen("-t", "ed25519", "-N", "", "-f", seedFile.path).exit)
        val real = (OpenSshKeyFormat.parsePrivate(seedFile.readText()) as SshResult.Success).value
        val file = File(dir, "mine")
        file.writeText(OpenSshKeyFormat.encodePrivate(real, "mine", "s3cret-é".toByteArray(), 5))
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))

        val good = run(dir, "ssh-keygen", "-y", "-P", "s3cret-é", "-f", file.path)
        assertEquals(0, good.exit)
        assertEquals(publicLine(OpenSshKeyFormat.publicLine(real, "")), publicLine(good.out))
        assertNotEquals(0, run(dir, "ssh-keygen", "-y", "-P", "other", "-f", file.path).exit)
    }

    private companion object {
        const val EXEC_FAILED = -1
        const val TIMEOUT_SECONDS = 60L
    }
}
