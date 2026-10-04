// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.data.rootfs.TarBuilder
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.BackupPhase
import com.qtekfun.ultimateterminal.domain.backup.BackupProgress
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BackupRestoreTest {
    @TempDir
    lateinit var dir: File
    private lateinit var old: Device
    private lateinit var new: Device

    @BeforeEach
    fun setUp() {
        old = Device(File(dir, "old"))
        new = Device(File(dir, "new"))
    }

    @AfterEach
    fun tearDown() {
        old.close()
        new.close()
    }

    private fun restore(bytes: ByteArray, password: String? = null, on: Device = new) =
        runBlocking { on.restorer().restore(BytesSource(bytes), password) }

    /** Nothing was installed and nothing was left behind. */
    private fun assertUntouched(device: Device = new) = runBlocking {
        assertEquals(emptyList<Any>(), device.distros.observeAll().first())
        assertEquals(
            emptyList<String>(),
            File(device.storageRoot, "distros").list().orEmpty().toList()
        )
        assertEquals(
            emptyList<String>(),
            File(device.storageRoot, "distros-tmp").list().orEmpty().toList()
        )
        assertEquals(ThemeMode.SYSTEM, device.settings.observe().first().themeMode)
    }

    private fun tree(root: File): Map<String, String> = root.walkTopDown().filter {
        it != root
    }.associate { file ->
        val path = file.toPath()
        val kind = when {
            Files.isSymbolicLink(path) -> "link->" + Files.readSymbolicLink(path)
            file.isDirectory -> "dir"
            else -> "file:" + file.readText()
        }
        val mode = if (Files.isSymbolicLink(
                path
            )
        ) {
            ""
        } else {
            PosixFilePermissions.toString(Files.getPosixFilePermissions(path))
        }
        file.relativeTo(root).path to "$kind $mode"
    }

    @Test
    fun aFullBackupMovesDistrosAndConfigurationToAnotherDevice() = runBlocking {
        val alpine = old.addDistro("Alpine") { root ->
            File(root, "bin").mkdirs()
            File(root, "bin/busybox").writeText("ELF")
            Files.setPosixFilePermissions(
                File(root, "bin/busybox").toPath(),
                PosixFilePermissions.fromString("rwxr-xr-x")
            )
            Files.createSymbolicLink(File(root, "bin/sh").toPath(), Paths.get("busybox"))
            File(root, "etc").mkdirs()
            File(root, "etc/motd").writeText("hola")
        }
        old.addDistro("Debian") { File(it, "hello").writeText("hi") }
        old.distros.setDefault(alpine.id)
        old.settings.update { it.copy(themeMode = ThemeMode.DARK, oledBlack = true) }
        val bytes = old.exportBytes(
            ExportRequest(BackupKind.ALL, includeSshKeys = false, password = "pw")
        )

        val summary = restore(bytes, "pw").value()

        assertEquals(2, summary.distros)
        assertTrue(summary.settingsApplied)
        val restored = new.distros.observeAll().first()
        assertEquals(setOf("Alpine", "Debian"), restored.map { it.name }.toSet())
        assertTrue(restored.all { it.state == DistroState.READY && it.sizeBytes > 0 })
        val alpineCopy = restored.first { it.name == "Alpine" }
        assertTrue(alpineCopy.isDefault)
        assertEquals(tree(old.distroDir(alpine)), tree(new.distroDir(alpineCopy)))
        assertEquals(ThemeMode.DARK, new.settings.observe().first().themeMode)
        assertTrue(new.settings.observe().first().oledBlack)
        assertEquals(
            emptyList<String>(),
            File(new.storageRoot, "distros-tmp").list().orEmpty().toList()
        )
    }

    @Test
    fun aSingleDistroBackupRestoresOnlyTheDistro() = runBlocking {
        val distro = old.addDistro("Alpine") { File(it, "x").writeText("1") }
        old.settings.update { it.copy(themeMode = ThemeMode.DARK) }
        val bytes = old.exportBytes(ExportRequest(BackupKind.DISTRO, distroId = distro.id))
        val summary = restore(bytes).value()
        assertEquals(1, summary.distros)
        assertFalse(summary.settingsApplied)
        assertEquals(ThemeMode.SYSTEM, new.settings.observe().first().themeMode)
    }

    @Test
    fun restoringTwiceKeepsBothUnderDifferentNames() = runBlocking {
        val distro = old.addDistro("Alpine")
        val bytes = old.exportBytes(ExportRequest(BackupKind.DISTRO, distroId = distro.id))
        repeat(3) { restore(bytes).value() }
        assertEquals(
            setOf("Alpine", "Alpine (restored)", "Alpine (restored 2)"),
            new.distros.observeAll().first().map { it.name }.toSet()
        )
    }

    @Test
    fun theFileIsToldEncryptedOrNotBeforeAskingForAPassword() = runBlocking {
        val plain = old.exportBytes(ExportRequest(BackupKind.CONFIG, includeSshKeys = false))
        val encrypted = old.exportBytes(
            ExportRequest(BackupKind.CONFIG, includeSshKeys = false, password = "pw")
        )
        assertFalse(new.restorer().probe(BytesSource(plain)).value().encrypted)
        assertTrue(new.restorer().probe(BytesSource(encrypted)).value().encrypted)
        for (junk in listOf(
            ByteArray(0),
            "manifes".toByteArray(),
            "hello world, not a backup".toByteArray()
        )) {
            assertEquals(
                BackupError.NotABackup,
                new.restorer().probe(BytesSource(junk)).errorOrNull()
            )
        }
    }

    @Test
    fun anEncryptedBackupNeedsTheRightPasswordAndChangesNothingOtherwise() = runBlocking {
        old.addDistro("Alpine")
        val bytes = old.exportBytes(
            ExportRequest(BackupKind.ALL, includeSshKeys = false, password = "pw")
        )
        assertEquals(BackupError.PasswordRequired, restore(bytes).errorOrNull())
        assertEquals(BackupError.PasswordRequired, restore(bytes, "").errorOrNull())
        assertEquals(BackupError.WrongPasswordOrCorrupt, restore(bytes, "other").errorOrNull())
        assertUntouched()
    }

    @Test
    fun anEncryptedFileCutShortInTheHeaderIsTruncated() {
        val bytes = BackupBuilder("CONFIG").config().encrypted("pw").build()
        assertEquals(BackupError.Truncated, restore(bytes.copyOf(20), "pw").errorOrNull())
    }

    @Test
    fun anEncryptedHeaderWithAbsurdLimitsIsNotABackup() {
        val bytes = BackupBuilder("CONFIG").config().encrypted("pw").build()
        bytes[8] = 0x7F // the most significant byte of the iteration count
        assertEquals(BackupError.NotABackup, restore(bytes, "pw").errorOrNull())
    }

    @Test
    fun aChangedByteInTheDistroIsCaughtBeforeAnythingIsInstalled() = runBlocking {
        old.addDistro("Alpine") {
            File(it, "data").writeBytes(ByteArray(2000) { b -> (b * 31).toByte() })
        }
        val bytes = old.exportBytes(ExportRequest(BackupKind.ALL, includeSshKeys = false))
        val changed = bytes.copyOf().also { it[it.size - 2000] = (it[it.size - 2000] + 1).toByte() }
        assertEquals(BackupError.HashMismatch("distros/0.tar.gz"), restore(changed).errorOrNull())
        assertUntouched()
    }

    @Test
    fun aFileCutShortIsTruncatedAndChangesNothing() = runBlocking {
        old.addDistro("Alpine") {
            File(it, "data").writeBytes(ByteArray(5000) { b -> (b * 31).toByte() })
        }
        val bytes = old.exportBytes(ExportRequest(BackupKind.ALL, includeSshKeys = false))
        assertEquals(BackupError.Truncated, restore(bytes.copyOf(bytes.size / 2)).errorOrNull())
        assertUntouched()
    }

    @Test
    fun somethingThatIsNotABackupIsRefused() {
        for (junk in listOf(
            ByteArray(0),
            "just some text that is long enough".toByteArray(),
            TarBuilder().file("readme.txt", "hi").tar()
        )) {
            assertEquals(BackupError.NotABackup, restore(junk).errorOrNull())
        }
    }

    @Test
    fun theManifestMustBeTheFirstEntry() {
        val out = java.io.ByteArrayOutputStream()
        ContainerWriter(out).use {
            it.bytes(PartNames.CONFIG, ConfigCodec.encode(sampleSnapshot()))
        }
        assertEquals(BackupError.NotABackup, restore(out.toByteArray()).errorOrNull())
        val empty = java.io.ByteArrayOutputStream().also { ContainerWriter(it).close() }
        assertEquals(BackupError.NotABackup, restore(empty.toByteArray()).errorOrNull())
    }

    @Test
    fun aHugeOrBrokenManifestIsRefused() {
        val huge = BackupBuilder("CONFIG").rawManifest(
            ByteArray(ManifestCodec.MAX_BYTES + 1) {
                ' '.code.toByte()
            }
        )
        assertEquals(
            BackupError.InvalidManifest("the manifest is too big"),
            restore(huge.build()).errorOrNull()
        )
        val broken = BackupBuilder("CONFIG").rawManifest("{not json".toByteArray())
        assertTrue(restore(broken.build()).errorOrNull() is BackupError.InvalidManifest)
    }

    @Test
    fun aFormatFromTheFutureIsNotGuessedAt() {
        val manifest = ManifestCodec.encode(
            BackupManifest(2, "9.9", "CONFIG", "2030-01-01T00:00:00Z", emptyList())
        )
        assertEquals(
            BackupError.UnsupportedVersion(2),
            restore(BackupBuilder().rawManifest(manifest).build()).errorOrNull()
        )
    }

    @Test
    fun partsThatDoNotMatchTheManifestAreRefused() {
        val unlisted = BackupBuilder("CONFIG").config().unlisted(PartNames.distro(0), ByteArray(3))
        assertEquals(
            BackupError.InvalidManifest("a part is not in the manifest"),
            restore(unlisted.build()).errorOrNull()
        )
        val repeated = BackupBuilder(
            "CONFIG"
        ).config().unlisted(PartNames.CONFIG, ConfigCodec.encode(sampleSnapshot()))
        assertEquals(
            BackupError.InvalidManifest("a part is repeated"),
            restore(repeated.build()).errorOrNull()
        )
        val missing = BackupBuilder("ALL").config().missing(PartNames.distro(0))
        assertEquals(BackupError.Truncated, restore(missing.build()).errorOrNull())
    }

    @Test
    fun aPartWithTheWrongHashOrSizeIsRefused() {
        val bytes = ConfigCodec.encode(sampleSnapshot())
        val badHash = BackupBuilder("CONFIG").part(PartNames.CONFIG, bytes, hash = "f".repeat(64))
        assertEquals(
            BackupError.HashMismatch(PartNames.CONFIG),
            restore(badHash.build()).errorOrNull()
        )
        val badSize = BackupBuilder("CONFIG").part(PartNames.CONFIG, bytes, size = bytes.size + 1L)
        assertEquals(
            BackupError.HashMismatch(PartNames.CONFIG),
            restore(badSize.build()).errorOrNull()
        )
    }

    @Test
    fun aBrokenConfigurationIsRefusedEvenWithAMatchingHash() {
        assertEquals(
            BackupError.InvalidConfig("not a configuration"),
            restore(BackupBuilder("CONFIG").config("{oops".toByteArray()).build()).errorOrNull()
        )
        val big = ByteArray(16 * 1024 * 1024 + 1)
        assertEquals(
            BackupError.InvalidManifest("the configuration is too big"),
            restore(BackupBuilder("CONFIG").config(big).build()).errorOrNull()
        )
    }

    @Test
    fun aFileThatChangesBetweenThePassesIsStillCheckedAndRolledBack() = runBlocking {
        val good = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "one")).build()
        val swapped = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "two!!")).build()
        val result = new.restorer().restore(BytesSource(good, swapped))
        assertEquals(BackupError.HashMismatch("distros/0.tar.gz"), result.errorOrNull())
        assertUntouched()
    }

    @Test
    fun aHostileArchiveInsideAPartIsStoppedAndRolledBack() = runBlocking {
        val hostile = TarBuilder().dir("etc").file("../escape", "x").gzip()
        val bytes = BackupBuilder("ALL").config().distro(0, hostile).build()
        val error = restore(bytes).errorOrNull()
        assertTrue(error is BackupError.Extraction && error.error is ExtractionError.UnsafeEntry)
        assertUntouched()
        assertFalse(File(dir, "new/escape").exists())
    }

    @Test
    fun aPartThatIsNotAGzipTarIsStoppedAndRolledBack() = runBlocking {
        val bytes = BackupBuilder("ALL").config().distro(0, ByteArray(100) { 7 }).build()
        assertTrue(restore(bytes).errorOrNull() is BackupError.Extraction)
        assertUntouched()
    }

    @Test
    fun notEnoughSpaceStopsTheRestoreBeforeUnpacking() = runBlocking {
        val bytes = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "one")).build()
        val files = FlakyFileSystem(new.fileSystem, freeSpace = 10)
        val error = new.restorer(files = files).restore(BytesSource(bytes)).errorOrNull()
        assertTrue(error is BackupError.InsufficientSpace && error.availableBytes == 10L)
        assertUntouched()
    }

    @Test
    fun aNameThatCannotBeFoundOrACrashingRegistryIsReportedAndRolledBack() = runBlocking {
        val bytes = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "one")).build()
        val taken = BackupRepositories(
            new.settings,
            new.profiles,
            new.layouts,
            new.hosts,
            FlakyDistros(new.distros, addError = DomainError.NameTaken("x")),
            new.keys
        )
        assertEquals(
            BackupError.Io("no free name for Alpine"),
            new.restorer(taken).restore(BytesSource(bytes)).errorOrNull()
        )
        val invalid = BackupRepositories(
            new.settings,
            new.profiles,
            new.layouts,
            new.hosts,
            FlakyDistros(new.distros, addError = DomainError.InvalidName("")),
            new.keys
        )
        assertTrue(
            new.restorer(invalid).restore(BytesSource(bytes)).errorOrNull() is BackupError.Io
        )
        assertUntouched()
    }

    @Test
    fun aFailureWhilePublishingRemovesTheDistro() = runBlocking {
        val bytes = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "one")).build()
        val cannotMove = new.restorer(files = FlakyFileSystem(new.fileSystem, failMove = true))
        assertTrue(cannotMove.restore(BytesSource(bytes)).errorOrNull() is BackupError.Io)
        assertUntouched()
        val repos = BackupRepositories(
            new.settings,
            new.profiles,
            new.layouts,
            new.hosts,
            FlakyDistros(new.distros, failUpdate = true),
            new.keys
        )
        assertTrue(new.restorer(repos).restore(BytesSource(bytes)).errorOrNull() is BackupError.Io)
        assertUntouched()
    }

    @Test
    fun aSourceThatCannotBeOpenedIsAnError() = runBlocking {
        val broken = object : com.qtekfun.ultimateterminal.domain.backup.BackupSource {
            override suspend fun open(): java.io.InputStream =
                throw java.io.IOException("permission denied")
        }
        assertEquals(
            BackupError.Io("permission denied"),
            new.restorer().restore(broken).errorOrNull()
        )
        assertEquals(
            BackupError.Io("permission denied"),
            new.restorer().probe(broken).errorOrNull()
        )
    }

    @Test
    fun theRestoreReportsItsPhases() = runBlocking {
        val bytes = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "one")).build()
        val progress = ArrayList<BackupProgress>()
        new.restorer().restore(BytesSource(bytes)) { progress.add(it) }.value()
        assertEquals(
            setOf(BackupPhase.VERIFYING, BackupPhase.RESTORING, BackupPhase.APPLYING),
            progress.map { it.phase }.toSet()
        )
    }

    @Test
    fun anEncryptedFileWhoseContentIsNotABackupIsRefusedAfterDecrypting() {
        val empty = java.io.ByteArrayOutputStream().also {
            ContainerWriter(it).close()
        }.toByteArray()
        assertEquals(
            BackupError.NotABackup,
            restore(BackupBuilder.encrypt(empty, "pw"), "pw").errorOrNull()
        )
        val configFirst = java.io.ByteArrayOutputStream()
        ContainerWriter(configFirst).use {
            it.bytes(PartNames.CONFIG, ConfigCodec.encode(sampleSnapshot()))
        }
        assertEquals(
            BackupError.NotABackup,
            restore(BackupBuilder.encrypt(configFirst.toByteArray(), "pw"), "pw").errorOrNull()
        )
    }

    @Test
    fun aPartThatGrowsBetweenThePassesIsCaughtByItsSizeWhateverTheCompressor() = runBlocking {
        // The extractor stops at the end of the archive and ignores what follows it, so this part
        // unpacks fine: only the byte count tells it is not the one the first pass checked. The test
        // does not depend on how well the JDK's zlib compresses, unlike a swapped file of similar size.
        val rootfs = BackupBuilder.rootfs("etc/a" to "one")
        val good = BackupBuilder("ALL").config().distro(0, rootfs).build()
        val grown = BackupBuilder("ALL").config().distro(0, rootfs + ByteArray(37)).build()

        val result = new.restorer().restore(BytesSource(good, grown))

        assertEquals(BackupError.HashMismatch("distros/0.tar.gz"), result.errorOrNull())
        assertUntouched()
    }

    @Test
    fun aReplacementOfTheSameSizeButOtherContentIsCaughtByTheHash() = runBlocking {
        val good = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "one")).build()
        val other = BackupBuilder(
            "ALL"
        ).config().distro(0, BackupBuilder.rootfs("etc/a" to "two")).build()
        assertEquals(
            BackupError.HashMismatch("distros/0.tar.gz"),
            new.restorer().restore(BytesSource(good, other)).errorOrNull()
        )
        assertUntouched()
    }

    @Test
    fun theDefaultsOfTheRestorerWork() = runBlocking {
        val bytes = BackupBuilder(
            "DISTRO"
        ).distro(0, BackupBuilder.rootfs("etc/a" to "one")).build()
        val restorer = BackupRestorer(new.repositories, new.fileSystem, new.extractor)
        assertEquals(1, restorer.restore(BytesSource(bytes)).value().distros)
    }
}
