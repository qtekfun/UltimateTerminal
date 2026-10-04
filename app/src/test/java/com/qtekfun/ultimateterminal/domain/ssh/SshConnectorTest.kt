// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.fakes.CountingTokens
import com.qtekfun.ultimateterminal.fakes.FakeDistroRepository
import com.qtekfun.ultimateterminal.fakes.FakeSshHostRepository
import com.qtekfun.ultimateterminal.fakes.FakeSshKeyStore
import com.qtekfun.ultimateterminal.fakes.InMemorySecretFileStore
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SshConnectorTest {
    private val hosts = FakeSshHostRepository()
    private val distros = FakeDistroRepository()
    private val keys = FakeSshKeyStore()
    private val files = InMemorySecretFileStore()
    private val connector = SshConnector(hosts, distros, keys, files, CountingTokens())
    private val pem =
        "-----BEGIN OPENSSH PRIVATE KEY-----\nSECRET\n-----END OPENSSH PRIVATE KEY-----\n"

    private fun path(raw: String): FsPath = (FsPath.of(raw) as Outcome.Success).value

    private suspend fun distro(
        name: String,
        dir: String,
        state: DistroState = DistroState.READY
    ): Distro {
        val added = (
            distros.add(
                NewDistro(name, DistroType.ALPINE, "3.22", path(dir))
            ) as Outcome.Success
            ).value
        distros.updateState(added.id, state, 1L)
        return checkNotNull(distros.get(added.id))
    }

    private suspend fun keyAlias(): String {
        val alias = keys.newAlias()
        keys.put(
            SshKeyInfo(
                alias,
                "k",
                SshKeyType.ED25519,
                "ssh-ed25519 AAAA",
                "SHA256:x",
                Instant.EPOCH
            ),
            pem
        )
        return alias
    }

    private suspend fun host(
        keyAlias: String? = null,
        distroId: Long? = null,
        address: String = "example.com"
    ): SshHost = (
        hosts.add(
            SshHost(
                name = "web",
                host = address,
                user = "deploy",
                keyAlias = keyAlias,
                distroId = distroId
            )
        ) as Outcome.Success
        ).value

    private suspend fun plan(id: Long): SshLaunchPlan =
        (connector.prepare(id) as SshResult.Success).value

    private suspend fun failure(id: Long): SshError =
        (connector.prepare(id) as SshResult.Failure).error

    private fun tmpFiles(dir: String = "distros/a") = files.files.keys.filter {
        it.startsWith("$dir/tmp/")
    }

    @Test
    fun aHostThatDoesNotExistIsReported() = runTest {
        distro("a", "distros/a")
        assertEquals(SshError.HostNotFound, failure(99))
    }

    @Test
    fun withoutAReadyDistroThereIsNothingToRunIn() = runTest {
        val h = host()
        assertEquals(SshError.NoDistro, failure(h.id))
        distro("half", "distros/half", DistroState.INSTALLING)
        assertEquals(SshError.NoDistro, failure(h.id))
    }

    @Test
    fun aHostPinnedToADistroThatIsNotReadyDoesNotFallBackToAnother() = runTest {
        distro("ready", "distros/ready")
        val broken = distro("broken", "distros/broken", DistroState.FAILED)
        val h = host(distroId = broken.id)
        assertEquals(SshError.NoDistro, failure(h.id))
    }

    @Test
    fun aHostWhoseDistroWasDeletedUsesTheDefaultOne() = runTest {
        val a = distro("a", "distros/a")
        val h = host(distroId = 4242)
        assertEquals(a.id, plan(h.id).distro.id)
    }

    @Test
    fun aHostPinnedToADistroUsesIt() = runTest {
        distro("a", "distros/a")
        val b = distro("b", "distros/b")
        val h = host(distroId = b.id)
        assertEquals(b.id, plan(h.id).distro.id)
    }

    @Test
    fun aHostWithoutAKeyWritesNoKeyFile() = runTest {
        distro("a", "distros/a")
        val p = plan(host().id)
        assertTrue(tmpFiles().isEmpty())
        assertEquals("deploy@example.com", p.guestCommand.last())
        assertFalse("-i" in p.guestCommand)
    }

    @Test
    fun aKeyIsMaterializedOwnerOnlyAndNamedInTheCommand() = runTest {
        distro("a", "distros/a")
        val h = host(keyAlias = keyAlias())
        val p = plan(h.id)
        val file = "distros/a/tmp/.ut-ssh-0000000000000001"
        assertEquals(listOf(file), tmpFiles())
        assertEquals(pem, String(files.files.getValue(file)))
        assertEquals(true, files.ownerOnly[file])
        assertEquals(
            "/tmp/.ut-ssh-0000000000000001",
            p.guestCommand[
                p.guestCommand.indexOf("-i") +
                    1
            ]
        )
    }

    @Test
    fun cleanupRemovesTheKeyFileAndCanRunTwice() = runTest {
        distro("a", "distros/a")
        val p = plan(host(keyAlias = keyAlias()).id)
        p.cleanup()
        assertTrue(tmpFiles().isEmpty())
        p.cleanup()
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun everyConnectionGetsItsOwnKeyFile() = runTest {
        distro("a", "distros/a")
        val h = host(keyAlias = keyAlias())
        val first = plan(h.id)
        val second = plan(h.id)
        assertEquals(2, tmpFiles().size)
        assertNotEquals(first.guestCommand, second.guestCommand)
        first.cleanup()
        assertEquals(1, tmpFiles().size)
    }

    @Test
    fun staleKeyFilesOfACrashedRunAreRemovedButLiveOnesAndOthersStay() = runTest {
        distro("a", "distros/a")
        files.files["distros/a/tmp/.ut-ssh-deadbeefdeadbeef"] = ByteArray(1)
        files.files["distros/a/tmp/notes.txt"] = ByteArray(1)
        val h = host(keyAlias = keyAlias())
        val live = plan(h.id)
        assertFalse("distros/a/tmp/.ut-ssh-deadbeefdeadbeef" in files.files)
        assertTrue("distros/a/tmp/notes.txt" in files.files)
        plan(h.id)
        // the first connection's file is still there: it is live, not stale
        assertEquals(2, tmpFiles().count { it.contains(".ut-ssh-") })
        live.cleanup()
    }

    @Test
    fun aKeyThatIsMissingFromTheStoreStopsTheConnection() = runTest {
        distro("a", "distros/a")
        val h = host(keyAlias = "k0000000000ff")
        assertEquals(SshError.KeyNotFound, failure(h.id))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun anInvalidHostLeavesNoKeyFileBehind() = runTest {
        distro("a", "distros/a")
        val h = host(address = "-oProxyCommand=x", keyAlias = keyAlias())
        assertEquals(SshError.Invalid("host"), failure(h.id))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun aKeyFileThatCannotBeWrittenStopsTheConnection() = runTest {
        distro("a", "distros/a")
        val h = host(keyAlias = keyAlias())
        files.failWrites = true
        assertEquals(SshError.Storage("cannot write the key file"), failure(h.id))
    }

    @Test
    fun aMissingResolverFileIsWrittenWithPublicNameservers() = runTest {
        distro("a", "distros/a")
        plan(host().id)
        val text = String(files.files.getValue("distros/a/etc/resolv.conf"))
        assertEquals("nameserver 1.1.1.1\nnameserver 9.9.9.9\n", text)
        assertEquals(false, files.ownerOnly["distros/a/etc/resolv.conf"])
    }

    @Test
    fun anEmptyResolverFileIsReplacedButOneTheUserSetUpIsKept() = runTest {
        distro("a", "distros/a")
        files.files["distros/a/etc/resolv.conf"] = ByteArray(0)
        plan(host().id)
        assertTrue(files.files.getValue("distros/a/etc/resolv.conf").isNotEmpty())
        files.files["distros/a/etc/resolv.conf"] = "nameserver 10.0.0.1\n".toByteArray()
        plan(host().id)
        assertEquals(
            "nameserver 10.0.0.1\n",
            String(files.files.getValue("distros/a/etc/resolv.conf"))
        )
    }

    @Test
    fun thePlanDoesNotPrintTheCommandLine() = runTest {
        distro("a", "distros/a")
        val p = plan(host(keyAlias = keyAlias()).id)
        assertFalse(p.toString().contains(".ut-ssh"))
    }
}
