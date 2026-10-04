// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.fakes.FakeSshHostRepository
import com.qtekfun.ultimateterminal.fakes.FakeSshKeyStore
import com.qtekfun.ultimateterminal.fakes.FixedKeyGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SshKeyServiceTest {
    private val ed = SshPrivateKey.Ed25519(ByteArray(32) { 1 }, ByteArray(32) { 2 })
    private val store = FakeSshKeyStore()
    private val hosts = FakeSshHostRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)

    private fun service(generator: SshKeyGenerator = FixedKeyGenerator(ed)) =
        SshKeyService(store, generator, hosts, clock)

    private fun info(result: SshResult<SshKeyInfo>): SshKeyInfo =
        (result as SshResult.Success).value

    private fun error(result: SshResult<*>): SshError = (result as SshResult.Failure).error

    private suspend fun storedCount(): Int = store.observe().first().size

    @Test
    fun aGeneratedKeyIsStoredWithItsPublicPartAndNoPrivateOne() = runTest {
        val key = info(service().generate("  laptop  ", SshKeyType.ED25519))
        assertEquals("laptop", key.name)
        assertEquals(SshKeyType.ED25519, key.type)
        assertTrue(key.publicKey.startsWith("ssh-ed25519 "))
        assertTrue(key.publicKey.endsWith(" laptop"))
        assertEquals(OpenSshKeyFormat.fingerprint(ed), key.fingerprint)
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), key.createdAt)
        assertFalse(key.toString().contains("BEGIN"))
    }

    @Test
    fun theStoredPrivateKeyIsTheOpenSshFileOfTheGeneratedKey() = runTest {
        val key = info(service().generate("laptop", SshKeyType.ED25519))
        val text = (service().exportPrivate(key.alias) as SshResult.Success).value
        assertEquals(OpenSshKeyFormat.encodePrivate(ed, "laptop"), text)
    }

    @Test
    fun anImportedKeyIsReadAndStored() = runTest {
        val text = OpenSshKeyFormat.encodePrivate(ed, "orig")
        val key = info(service().import("work", text))
        assertEquals("work", key.name)
        assertEquals(OpenSshKeyFormat.fingerprint(ed), key.fingerprint)
    }

    @Test
    fun anImportThatIsNotAKeyIsRefusedAndStoresNothing() = runTest {
        assertEquals(SshError.UnsupportedKeyFormat, error(service().import("x", "hello")))
        assertEquals(0, storedCount())
    }

    @Test
    fun namesAreUniqueIgnoringCase() = runTest {
        service().generate("Laptop", SshKeyType.ED25519)
        assertEquals(
            SshError.NameTaken("laptop"),
            error(service().generate("laptop", SshKeyType.ED25519))
        )
        assertEquals(1, storedCount())
    }

    @Test
    fun anInvalidNameIsRefused() = runTest {
        assertEquals(SshError.Invalid("name"), error(service().generate("   ", SshKeyType.ED25519)))
        assertEquals(
            SshError.Invalid("name"),
            error(service().generate("a\u0000b", SshKeyType.ED25519))
        )
        assertEquals(
            SshError.Invalid("name"),
            error(service().generate("x".repeat(65), SshKeyType.ED25519))
        )
    }

    @Test
    fun aTypeTheDeviceCannotGenerateIsReported() = runTest {
        val limited = service(FixedKeyGenerator(ed, supportedTypes = listOf(SshKeyType.RSA)))
        assertEquals(listOf(SshKeyType.RSA), limited.supportedTypes)
        assertEquals(
            SshError.UnsupportedKeyType(SshKeyType.ED25519),
            error(limited.generate("a", SshKeyType.ED25519))
        )
    }

    @Test
    fun aStorageFailureIsReportedAndNothingIsListed() = runTest {
        store.failPuts = true
        assertEquals(
            SshError.Storage("disk full"),
            error(service().generate("a", SshKeyType.ED25519))
        )
        assertEquals(0, storedCount())
    }

    @Test
    fun theLongestAcceptedNameWorks() = runTest {
        assertEquals(
            "x".repeat(64),
            info(service().generate("x".repeat(64), SshKeyType.ED25519)).name
        )
    }

    @Test
    fun theKeyOfASavedHostCannotBeRemoved() = runTest {
        val key = info(service().generate("laptop", SshKeyType.ED25519))
        hosts.add(SshHost(name = "a", host = "a.example", user = "u", keyAlias = key.alias))
        hosts.add(SshHost(name = "b", host = "b.example", user = "u", keyAlias = key.alias))
        hosts.add(SshHost(name = "c", host = "c.example", user = "u"))
        assertEquals(SshError.KeyInUse(2), error(service().remove(key.alias)))
        assertEquals(1, storedCount())
    }

    @Test
    fun aKeyNoHostUsesIsRemoved() = runTest {
        val key = info(service().generate("laptop", SshKeyType.ED25519))
        assertEquals(SshResult.Success(Unit), service().remove(key.alias))
        assertEquals(0, storedCount())
        assertEquals(SshError.KeyNotFound, error(service().exportPrivate(key.alias)))
    }

    @Test
    fun thePublicKeyCanBeExportedAndAnUnknownAliasIsNotFound() = runTest {
        val key = info(service().generate("laptop", SshKeyType.ED25519))
        assertEquals(SshResult.Success(key.publicKey), service().exportPublic(key.alias))
        assertEquals(SshError.KeyNotFound, error(service().exportPublic("k0000000000ff")))
    }
}
