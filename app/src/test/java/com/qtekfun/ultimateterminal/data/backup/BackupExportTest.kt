// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.BackupPhase
import com.qtekfun.ultimateterminal.domain.backup.BackupProgress
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.DistroType
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class BackupExportTest {
    @TempDir
    lateinit var dir: File
    private lateinit var device: Device

    @BeforeEach
    fun setUp() {
        device = Device(File(dir, "device"))
    }

    @AfterEach
    fun tearDown() = device.close()

    private fun leftovers() = File(device.storageRoot, "backup-tmp").listFiles().orEmpty().toList()

    private suspend fun addKey() = device.keys.put(
        SshKeyInfo(
            "k000000000001",
            "laptop",
            SshKeyType.ED25519,
            "ssh-ed25519 AAAA",
            "SHA256:x",
            Instant.EPOCH
        ),
        "-----BEGIN OPENSSH PRIVATE KEY-----"
    )

    @Test
    fun aPlainConfigurationBackupIsAReadableArchiveWithoutKeys() = runBlocking {
        addKey()
        device.hosts.add(
            SshHost(name = "web", host = "example.org", user = "admin", keyAlias = "k000000000001")
        )
        val sink = MemorySink()
        val summary = device.exporter()
            .export(ExportRequest(BackupKind.CONFIG, includeSshKeys = false), sink).value()
        assertEquals(BackupKind.CONFIG, summary.kind)
        assertFalse(summary.encrypted)
        assertEquals(0, summary.sshKeys)
        assertEquals(sink.bytes().size.toLong(), summary.bytesWritten)
        val text = String(sink.bytes(), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("manifest.json"))
        assertFalse("OPENSSH PRIVATE KEY" in text)
        assertTrue("example.org" in text)
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun privateKeysAreNeverWrittenWithoutAPassword() = runBlocking {
        addKey()
        val sink = MemorySink()
        assertEquals(
            BackupError.KeysNeedPassword,
            device.exporter().export(ExportRequest(BackupKind.ALL), sink).errorOrNull()
        )
        assertEquals(
            BackupError.KeysNeedPassword,
            device.exporter().export(
                ExportRequest(BackupKind.ALL, password = ""),
                sink
            ).errorOrNull()
        )
        assertFalse(sink.opened)
    }

    @Test
    fun withAPasswordNothingReadableIsInTheFile() = runBlocking {
        addKey()
        device.hosts.add(SshHost(name = "web", host = "example.org", user = "admin"))
        val sink = MemorySink()
        val summary = device.exporter()
            .export(ExportRequest(BackupKind.CONFIG, password = TEST_PHRASE), sink).value()
        assertTrue(summary.encrypted)
        assertEquals(1, summary.sshKeys)
        val text = String(sink.bytes(), Charsets.ISO_8859_1)
        assertTrue(text.startsWith(BackupCrypto.MAGIC))
        for (secret in listOf("manifest.json", "example.org", "OPENSSH", "web")) {
            assertFalse(secret in text, secret)
        }
    }

    @Test
    fun aDistroBackupNeedsAReadyDistro() = runBlocking {
        val installing = (
            device.distros.add(
                NewDistro(
                    "Half",
                    DistroType.ALPINE,
                    "3.20",
                    FsPath.of("distros/half").let {
                        (it as Outcome.Success).value
                    }
                )
            ) as Outcome.Success
            ).value
        val sink = MemorySink()
        for (id in listOf(9999L, installing.id, null)) {
            assertEquals(
                BackupError.DistroUnavailable,
                device.exporter().export(
                    ExportRequest(BackupKind.DISTRO, distroId = id),
                    sink
                ).errorOrNull()
            )
        }
        assertEquals(DistroState.INSTALLING, device.distros.get(installing.id)!!.state)
        assertFalse(sink.opened)
    }

    @Test
    fun aFullBackupHoldsEveryReadyDistroAndReportsProgress() = runBlocking {
        device.addDistro("One") { File(it, "a.txt").writeText("a") }
        device.addDistro("Two") { File(it, "b.txt").writeText("b") }
        device.distros.add(
            NewDistro(
                "Half",
                DistroType.DEBIAN,
                "12",
                FsPath.of("distros/half").let {
                    (it as Outcome.Success).value
                }
            )
        )
        val sink = MemorySink()
        val progress = ArrayList<BackupProgress>()
        val summary = device.exporter()
            .export(ExportRequest(BackupKind.ALL, includeSshKeys = false), sink) {
                progress.add(it)
            }.value()
        assertEquals(2, summary.distros)
        val phases = progress.map { it.phase }.toSet()
        assertEquals(
            setOf(BackupPhase.PREPARING, BackupPhase.ARCHIVING, BackupPhase.WRITING),
            phases
        )
        assertTrue(progress.mapNotNull { it.fraction }.all { it in 0f..1f })
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun aDistroBackupOfOneDistroHoldsOnlyThatOne() = runBlocking {
        device.addDistro("One")
        val two = device.addDistro("Two")
        val summary = device.exporter()
            .export(ExportRequest(BackupKind.DISTRO, distroId = two.id), MemorySink()).value()
        assertEquals(1, summary.distros)
        assertEquals(0, summary.sshKeys)
    }

    @Test
    fun notEnoughSpaceIsReportedBeforeAnythingIsWritten() = runBlocking {
        device.addDistro("One") { File(it, "big").writeBytes(ByteArray(10_000)) }
        val sink = MemorySink()
        val error = device.exporter(files = FlakyFileSystem(device.fileSystem, freeSpace = 100))
            .export(ExportRequest(BackupKind.ALL, includeSshKeys = false), sink).errorOrNull()
        assertTrue(
            error is BackupError.InsufficientSpace && error.availableBytes == 100L &&
                error.requiredBytes >= 10_000
        )
        assertFalse(sink.opened)
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun aWorkDirectoryThatCannotBeCreatedIsAnError() = runBlocking {
        val error = device.exporter(files = FlakyFileSystem(device.fileSystem, failCreate = true))
            .export(
                ExportRequest(BackupKind.CONFIG, includeSshKeys = false),
                MemorySink()
            ).errorOrNull()
        assertTrue(error is BackupError.Io)
    }

    @Test
    fun aDistroWhoseFilesVanishedFailsCleanly() = runBlocking {
        val distro = device.addDistro("One")
        device.distroDir(distro).deleteRecursively()
        val sink = MemorySink()
        val error = device.exporter()
            .export(ExportRequest(BackupKind.ALL, includeSshKeys = false), sink).errorOrNull()
        assertTrue(error is BackupError.Io)
        assertFalse(sink.opened)
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun aKeyThatCannotBeReadStopsTheExport() = runBlocking {
        addKey()
        val repos = BackupRepositories(
            device.settings,
            device.profiles,
            device.layouts,
            device.hosts,
            device.distros,
            UnreadableKeys(device.keys)
        )
        val error = device.exporter(
            repos
        ).export(ExportRequest(BackupKind.CONFIG, password = "x"), MemorySink()).errorOrNull()
        assertEquals(BackupError.Io("cannot read the SSH key k000000000001"), error)
    }

    @Test
    fun aDestinationThatCannotBeOpenedIsAnErrorAndNothingIsDiscarded() = runBlocking {
        val sink = FailingSink(limit = null)
        val error = device.exporter()
            .export(ExportRequest(BackupKind.CONFIG, includeSshKeys = false), sink).errorOrNull()
        assertEquals(BackupError.Io("cannot open the destination"), error)
        assertFalse(sink.discarded)
    }

    @Test
    fun aDestinationThatFillsUpMidWayIsDiscarded() = runBlocking {
        val sink = FailingSink(limit = 100)
        val error = device.exporter()
            .export(ExportRequest(BackupKind.CONFIG, includeSshKeys = false), sink).errorOrNull()
        assertEquals(BackupError.Io("No space left on device"), error)
        assertTrue(sink.opened)
        assertTrue(sink.discarded)
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun cancellingWhileArchivingCleansTheWorkDirectory() = runBlocking {
        device.addDistro("One") { File(it, "big").writeBytes(ByteArray(300_000)) }
        val sink = MemorySink()
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            device.exporter().export(ExportRequest(BackupKind.ALL, includeSshKeys = false), sink) {
                if (it.phase == BackupPhase.ARCHIVING) job.cancel()
            }
        }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(sink.opened)
        assertEquals(emptyList<File>(), leftovers())
    }

    @Test
    fun leftoversOfAnInterruptedExportAreRemoved() = runBlocking {
        File(device.storageRoot, "backup-tmp/old").mkdirs()
        File(device.storageRoot, "backup-tmp/old/distro-0.tar.gz").writeText("partial")
        device.exporter().recoverInterrupted()
        assertFalse(File(device.storageRoot, "backup-tmp").exists())
    }

    @Test
    fun theExportKeepsTheSettingsProfilesAndLayoutsReferencesByPosition() = runBlocking {
        val first = (device.profiles.add(Profile(name = "First")) as Outcome.Success).value
        device.profiles.add(Profile(name = "Second"))
        assertNull(device.profiles.get(9999))
        val sink = MemorySink()
        device.exporter().export(
            ExportRequest(BackupKind.CONFIG, includeSshKeys = false),
            sink
        ).value()
        val manifestBytes = ByteArrayInputStream(sink.bytes())
        ContainerReader(manifestBytes).use { reader ->
            reader.next()
            val config = ConfigCodec.decode(reader.next()!!.content.readBytes()).value()
            assertEquals(listOf("First", "Second"), config.profiles.map { it.name })
        }
        assertEquals("First", first.name)
    }

    @Test
    fun anEmptyPasswordMeansNoEncryption() = runBlocking {
        val sink = MemorySink()
        val summary = device.exporter()
            .export(
                ExportRequest(BackupKind.CONFIG, includeSshKeys = false, password = ""),
                sink
            ).value()
        assertFalse(summary.encrypted)
        assertTrue(String(sink.bytes(), Charsets.ISO_8859_1).startsWith("manifest.json"))
    }

    @Test
    fun theWorkPathsAreFixedAndRefuseUnsafeTokens() {
        assertEquals("backup-tmp", BackupPaths.tempRoot().value)
        assertEquals("backup-tmp/abc", BackupPaths.tempDirectory("abc").value)
        assertThrows<IllegalArgumentException> { BackupPaths.tempDirectory("../escape") }
    }

    @Test
    fun theDefaultEnvironmentUsesTheRealClockAndRandomness() = runBlocking {
        val environment = ExportEnvironment()
        assertEquals(16, environment.random(16).size)
        assertFalse(environment.random(16).contentEquals(environment.random(16)))
        assertTrue(environment.newToken().isNotEmpty())
        assertEquals(BackupCrypto.DEFAULT_ITERATIONS, environment.kdfIterations)
        val exporter = BackupExporter(device.repositories, device.fileSystem, "0.1.0")
        assertEquals(
            BackupKind.CONFIG,
            exporter.export(
                ExportRequest(BackupKind.CONFIG, includeSshKeys = false),
                MemorySink()
            ).value().kind
        )
    }
}

/** A made-up passphrase for the encryption tests; it protects nothing and is not a credential. */
private const val TEST_PHRASE = "open sesame"
