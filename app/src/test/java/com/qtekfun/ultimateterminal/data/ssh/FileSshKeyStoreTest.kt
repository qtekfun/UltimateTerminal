// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.ssh

import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.fakes.CountingTokens
import com.qtekfun.ultimateterminal.fakes.InMemorySecretFileStore
import com.qtekfun.ultimateterminal.fakes.testSecretBox
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileSshKeyStoreTest {
    private val files = InMemorySecretFileStore()
    private val store = FileSshKeyStore(files, testSecretBox(), CountingTokens())
    private val pem =
        "-----BEGIN OPENSSH PRIVATE KEY-----\nSECRET-BODY\n-----END OPENSSH PRIVATE KEY-----\n"

    private fun meta(
        version: Int = 1,
        type: String = "RSA",
        created: String = "2026-01-01T00:00:00Z"
    ) = """{"version":$version,"name":"n","type":"$type","publicKey":"p",""" +
        """"fingerprint":"f","createdAt":"$created"}"""

    private fun info(alias: String, name: String = "laptop") = SshKeyInfo(
        alias = alias,
        name = name,
        type = SshKeyType.ED25519,
        publicKey = "ssh-ed25519 AAAA $name",
        fingerprint = "SHA256:abc",
        createdAt = Instant.parse("2026-10-04T10:00:00Z")
    )

    @Test
    fun aStoredKeyIsListedAndItsPrivatePartComesBack() = runTest {
        assertEquals(SshResult.Success(Unit), store.put(info("k000000000001"), pem))
        assertEquals(listOf(info("k000000000001")), store.observe().first())
        assertEquals(SshResult.Success(pem), store.privateKey("k000000000001"))
    }

    @Test
    fun thePrivateKeyIsNeverOnDiskInThePlain() = runTest {
        store.put(info("k000000000001"), pem)
        val everything = files.files.values.joinToString("") { String(it, Charsets.ISO_8859_1) }
        assertFalse(everything.contains("SECRET-BODY"))
        assertFalse(everything.contains("OPENSSH PRIVATE KEY"))
    }

    @Test
    fun bothFilesAreOwnerOnly() = runTest {
        store.put(info("k000000000001"), pem)
        assertEquals(
            setOf("ssh-keys/k000000000001.key", "ssh-keys/k000000000001.meta"),
            files.ownerOnly.keys
        )
        assertTrue(files.ownerOnly.values.all { it })
    }

    @Test
    fun theListIsSortedByNameIgnoringCase() = runTest {
        store.put(info("k000000000001", "zeta"), pem)
        store.put(info("k000000000002", "Alpha"), pem)
        assertEquals(listOf("Alpha", "zeta"), store.observe().first().map { it.name })
    }

    @Test
    fun theObservedListFollowsPutsAndDeletes() = runTest {
        assertEquals(emptyList<SshKeyInfo>(), store.observe().first())
        store.put(info("k000000000001"), pem)
        assertEquals(1, store.observe().first().size)
        store.delete("k000000000001")
        assertEquals(0, store.observe().first().size)
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun deletingAKeyThatIsNotThereIsFine() = runTest {
        assertEquals(SshResult.Success(Unit), store.delete("k000000000009"))
    }

    @Test
    fun aKeyWithoutItsListEntryIsNotListedButCanBeDeleted() = runTest {
        store.put(info("k000000000001"), pem)
        files.files.remove("ssh-keys/k000000000001.meta")
        assertEquals(emptyList<SshKeyInfo>(), store.observe().first())
        store.delete("k000000000001")
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun aDamagedOrForeignListEntryIsSkipped() = runTest {
        store.put(info("k000000000001"), pem)
        files.files["ssh-keys/k000000000002.meta"] = "not json".toByteArray()
        files.files["ssh-keys/k000000000003.meta"] = meta(version = 9).toByteArray()
        files.files["ssh-keys/k000000000004.meta"] = meta(type = "DSA").toByteArray()
        files.files["ssh-keys/k000000000005.meta"] = meta(created = "yesterday").toByteArray()
        files.files["ssh-keys/readme.txt"] = "hello".toByteArray()
        files.files["ssh-keys/evil.meta"] = meta().toByteArray()
        files.files["ssh-keys/k0000000000zz.meta"] = meta().toByteArray()
        assertEquals(listOf("k000000000001"), store.observe().first().map { it.alias })
    }

    @Test
    fun aMissingKeyIsReportedAsNotFound() = runTest {
        assertEquals(SshResult.Failure(SshError.KeyNotFound), store.privateKey("k000000000001"))
    }

    @Test
    fun aBlobSwappedBetweenAliasesCannotBeOpened() = runTest {
        store.put(info("k000000000001"), pem)
        store.put(
            info("k000000000002", "other"),
            "-----BEGIN OPENSSH PRIVATE KEY-----\nOTHER\n-----END OPENSSH PRIVATE KEY-----\n"
        )
        val first = files.files.getValue("ssh-keys/k000000000001.key")
        files.files["ssh-keys/k000000000002.key"] = first
        assertEquals(SshResult.Failure(SshError.CorruptKey), store.privateKey("k000000000002"))
    }

    @Test
    fun anAliasThatCouldNameAnotherFileIsRefused() = runTest {
        listOf(
            "../x",
            "k1",
            "",
            "k000000000001/../../etc",
            "K000000000001",
            "k00000000000g"
        ).forEach { bad ->
            assertEquals(
                SshResult.Failure(SshError.Invalid("alias")),
                store.put(info(bad), pem),
                bad
            )
            assertEquals(SshResult.Failure(SshError.KeyNotFound), store.privateKey(bad), bad)
            assertEquals(SshResult.Failure(SshError.Invalid("alias")), store.delete(bad), bad)
        }
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun aFailedWriteIsReportedAndLeavesNoListedKey() = runTest {
        files.failWrites = true
        val result = store.put(info("k000000000001"), pem)
        assertEquals(SshResult.Failure(SshError.Storage("cannot write the key store")), result)
        assertEquals(emptyList<SshKeyInfo>(), store.observe().first())
    }

    @Test
    fun aKeyThatCannotBeSealedIsNotStored() = runTest {
        val broken =
            FileSshKeyStore(
                files,
                AesGcmSecretBox {
                    throw IllegalStateException("no key")
                },
                CountingTokens()
            )
        val result = runCatching { broken.put(info("k000000000001"), pem) }
        // The box reports a GeneralSecurityException as a failure; any other exception is a bug
        // and must surface rather than be hidden.
        assertTrue(result.isFailure)
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun newAliasesAreUniqueAndValid() {
        val a = store.newAlias()
        val b = store.newAlias()
        assertTrue(a != b)
        assertTrue(Regex("k[0-9a-f]{12}").matches(a))
        assertEquals(13, b.length)
    }
}
