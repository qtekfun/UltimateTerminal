// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.fakes.FakeSshHostRepository
import com.qtekfun.ultimateterminal.fakes.FakeSshKeyStore
import com.qtekfun.ultimateterminal.fakes.FixedKeyGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SshKeyServiceProtectedTest {
    private val ed = SshPrivateKey.Ed25519(ByteArray(32) { 1 }, ByteArray(32) { 2 })
    private val store = FakeSshKeyStore()
    private val cache = PassphraseCache()
    private val service =
        SshKeyService(store, FixedKeyGenerator(ed), FakeSshHostRepository(), unlocks = cache)
    private val pass = "hunter22".toCharArray()

    private fun info(result: SshResult<SshKeyInfo>) = (result as SshResult.Success).value

    private fun error(result: SshResult<*>) = (result as SshResult.Failure).error

    private suspend fun stored(alias: String) = (store.privateKey(alias) as SshResult.Success).value

    private suspend fun fingerprintOf(alias: String, passphrase: ByteArray?) =
        OpenSshKeyFormat.fingerprint(
            (OpenSshKeyFormat.parsePrivate(stored(alias), passphrase) as SshResult.Success).value
        )

    @Test
    fun aGeneratedKeyWithAPassphraseIsStoredEncryptedAndFlagged() = runTest {
        val key = info(service.generate("laptop", SshKeyType.ED25519, pass.copyOf()))
        assertTrue(key.passphraseProtected)
        assertTrue(OpenSshKeyFormat.isProtected(stored(key.alias)))
        assertEquals(
            OpenSshKeyFormat.fingerprint(ed),
            fingerprintOf(key.alias, "hunter22".toByteArray())
        )
        assertEquals(
            SshError.WrongPassphrase,
            error(OpenSshKeyFormat.parsePrivate(stored(key.alias), "other-one".toByteArray()))
        )
    }

    @Test
    fun withoutAPassphraseNothingChanges() = runTest {
        val key = info(service.generate("laptop", SshKeyType.ED25519, CharArray(0)))
        assertFalse(key.passphraseProtected)
        assertEquals(OpenSshKeyFormat.encodePrivate(ed, "laptop"), stored(key.alias))
    }

    @Test
    fun aPassphraseThatIsTooShortIsRefusedAndNothingIsStored() = runTest {
        assertEquals(
            SshError.PassphraseTooShort(Passphrases.MIN_LENGTH),
            error(service.generate("laptop", SshKeyType.ED25519, "abc".toCharArray()))
        )
        assertTrue(store.observe().first().isEmpty())
    }

    @Test
    fun theCallersPassphraseIsLeftForTheCallerToWipe() = runTest {
        val given = pass.copyOf()
        service.generate("laptop", SshKeyType.ED25519, given)
        assertEquals("hunter22", String(given))
    }

    @Test
    fun aProtectedKeyIsImportedWithItsPassphraseAndStaysProtectedByIt() = runTest {
        val vector = ProtectedKeyFixtures.ctr
        val key = info(service.import("work", vector.pem(), vector.passphrase.toCharArray()))
        assertTrue(key.passphraseProtected)
        assertEquals(vector.fingerprint, key.fingerprint)
        assertEquals(vector.fingerprint, fingerprintOf(key.alias, vector.passphrase.toByteArray()))
    }

    @Test
    fun aProtectedKeyImportedWithoutOrWithAWrongPassphraseIsRefused() = runTest {
        val vector = ProtectedKeyFixtures.ctr
        assertEquals(SshError.PassphraseRequired, error(service.import("work", vector.pem())))
        assertEquals(
            SshError.WrongPassphrase,
            error(service.import("work", vector.pem(), "nope-nope".toCharArray()))
        )
        assertTrue(store.observe().first().isEmpty())
    }

    @Test
    fun anUnprotectedKeyImportedWithAPassphraseStaysUnprotected() = runTest {
        val key = info(service.import("work", OpenSshKeyFormat.encodePrivate(ed, "x"), pass))
        assertFalse(key.passphraseProtected)
    }

    @Test
    fun aPassphraseCanBeAddedChangedAndRemoved() = runTest {
        val key = info(service.generate("laptop", SshKeyType.ED25519))
        val fingerprint = key.fingerprint

        val added = info(service.changePassphrase(key.alias, null, "first-pass".toCharArray()))
        assertTrue(added.passphraseProtected)
        assertTrue(store.observe().first().single().passphraseProtected)
        assertEquals(fingerprint, fingerprintOf(key.alias, "first-pass".toByteArray()))

        val changed = info(
            service.changePassphrase(
                key.alias,
                "first-pass".toCharArray(),
                "second-pass".toCharArray()
            )
        )
        assertTrue(changed.passphraseProtected)
        assertEquals(fingerprint, fingerprintOf(key.alias, "second-pass".toByteArray()))
        assertEquals(
            SshError.WrongPassphrase,
            error(OpenSshKeyFormat.parsePrivate(stored(key.alias), "first-pass".toByteArray()))
        )

        val removed = info(service.changePassphrase(key.alias, "second-pass".toCharArray(), null))
        assertFalse(removed.passphraseProtected)
        assertFalse(store.observe().first().single().passphraseProtected)
        assertEquals(fingerprint, fingerprintOf(key.alias, null))
    }

    @Test
    fun changingNeedsTheCurrentPassphrase() = runTest {
        val key = info(service.generate("laptop", SshKeyType.ED25519, pass.copyOf()))
        val before = stored(key.alias)
        assertEquals(
            SshError.PassphraseRequired,
            error(service.changePassphrase(key.alias, null, "second-pass".toCharArray()))
        )
        assertEquals(
            SshError.WrongPassphrase,
            error(service.changePassphrase(key.alias, "nope-nope".toCharArray(), null))
        )
        assertEquals(before, stored(key.alias))
        assertTrue(store.observe().first().single().passphraseProtected)
    }

    @Test
    fun aNewPassphraseThatIsTooShortOrAKeyThatDoesNotExistIsRefused() = runTest {
        val key = info(service.generate("laptop", SshKeyType.ED25519))
        assertEquals(
            SshError.PassphraseTooShort(Passphrases.MIN_LENGTH),
            error(service.changePassphrase(key.alias, null, "abc".toCharArray()))
        )
        assertEquals(
            SshError.KeyNotFound,
            error(service.changePassphrase("k0000000000ff", null, pass.copyOf()))
        )
    }

    @Test
    fun changingOrRemovingAKeyForgetsWhatWasRememberedForIt() = runTest {
        val key = info(service.generate("laptop", SshKeyType.ED25519, pass.copyOf()))
        cache.remember(key.alias, "hunter22".toByteArray())
        service.changePassphrase(key.alias, pass.copyOf(), "second-pass".toCharArray())
        assertNull(cache.recall(key.alias))

        cache.remember(key.alias, "second-pass".toByteArray())
        service.remove(key.alias)
        assertNull(cache.recall(key.alias))
    }

    @Test
    fun theExportOfAProtectedKeyIsStillEncrypted() = runTest {
        val key = info(service.generate("laptop", SshKeyType.ED25519, pass.copyOf()))
        val exported = (service.exportPrivate(key.alias) as SshResult.Success).value
        assertTrue(OpenSshKeyFormat.isProtected(exported))
        assertArrayEquals(
            OpenSshKeyFormat.publicBlob(ed),
            OpenSshKeyFormat.publicBlob(
                (
                    OpenSshKeyFormat.parsePrivate(
                        exported,
                        "hunter22".toByteArray()
                    ) as SshResult.Success
                    ).value
            )
        )
    }
}
