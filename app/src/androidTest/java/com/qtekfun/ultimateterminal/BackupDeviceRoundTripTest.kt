// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.backup.BackupSink
import com.qtekfun.ultimateterminal.domain.backup.BackupSource
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.support.GuestTerminal
import com.qtekfun.ultimateterminal.support.TestStorage
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class FileSink(private val file: File) : BackupSink {
    override suspend fun open(): OutputStream = file.outputStream()

    override suspend fun discard() {
        file.delete()
    }
}

private class FileSource(private val file: File) : BackupSource {
    override suspend fun open(): InputStream = file.inputStream()
}

/**
 * Exports a real Alpine distro and the configuration to a file on the device's storage, restores
 * it into a second, empty private storage, and checks that every file of the distro arrived and
 * that proot can still start the restored copy. Needs network for the Alpine download (skipped
 * without it); everything lives in directories of its own that are deleted afterwards.
 */
@RunWith(AndroidJUnit4::class)
class BackupDeviceRoundTripTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var source: TestStorage
    private lateinit var target: TestStorage

    @Before
    fun setUp() {
        source = TestStorage(context)
        target = TestStorage(context)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun anEncryptedBackupOfARealDistroRestoresOnAnEmptyStorage() = runBlocking {
        val distro = source.installAlpine("Alpine IT")
        source.settings.update { it.copy(themeMode = ThemeMode.LIGHT, terminalFontSizeSp = 17f) }
        source.profiles.add(Profile(name = "IT profile", startupCommand = "uname -a"))
        val backup = source.scratch("backup.utbackup")

        val exported = source.exporter.export(
            ExportRequest(BackupKind.ALL, password = PASSWORD),
            FileSink(backup)
        )
        assertTrue("export: $exported", exported is BackupResult.Success)
        assertTrue(backup.length() > 0)

        val restored = target.restorer.restore(FileSource(backup), PASSWORD)
        assertTrue("restore: $restored", restored is BackupResult.Success)

        val copy = target.distros.observeAll().first().single()
        assertEquals(distro.name, copy.name)
        assertEquals(distro.type, copy.type)
        assertTrue(copy.isDefault)
        assertEquals(
            treeOf(File(source.storageRoot, distro.directory.value).toPath()),
            treeOf(File(target.storageRoot, copy.directory.value).toPath())
        )
        val settings = target.settings.observe().first()
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(17f, settings.terminalFontSizeSp)
        assertEquals(
            listOf("IT profile" to "uname -a"),
            target.profiles.observeAll().first().map { it.name to it.startupCommand }
        )
        assertEquals(AppSettings().sharedStorage, settings.sharedStorage)
        assertRestoredDistroBoots(copy)
    }

    @Test
    fun aWrongPasswordRestoresNothing() = runBlocking {
        source.installAlpine("Alpine IT")
        val backup = source.scratch("wrong.utbackup")
        source.exporter.export(ExportRequest(BackupKind.ALL, password = PASSWORD), FileSink(backup))

        val result = target.restorer.restore(FileSource(backup), "not the password")

        assertTrue(result is BackupResult.Failure)
        assertEquals(emptyList<Any>(), target.distros.observeAll().first())
        assertFalse(File(target.storageRoot, "distros").list().orEmpty().isNotEmpty())
    }

    private fun assertRestoredDistroBoots(copy: Distro) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        GuestTerminal(context, instrumentation).use { terminal ->
            terminal.start(target.launchOf(copy), GridSize(80, 24))
            terminal.type("cat /etc/os-release")
            assertTrue(
                "the restored distro did not start",
                terminal.awaitLine {
                    it == "ID=alpine"
                }
            )
        }
    }

    /** Relative path to content hash, link target or "dir": the whole tree, links not followed. */
    private fun treeOf(root: Path): Map<String, String> = Files.walk(root).use { paths ->
        paths.filter { it != root }.toList().associate { path ->
            root.relativize(path).toString() to when {
                Files.isSymbolicLink(path) -> "-> " + Files.readSymbolicLink(path)
                Files.isDirectory(path) -> "dir"
                else -> sha256(path)
            }
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PASSWORD = "correct horse battery staple"
        const val BUFFER = 64 * 1024
    }
}
