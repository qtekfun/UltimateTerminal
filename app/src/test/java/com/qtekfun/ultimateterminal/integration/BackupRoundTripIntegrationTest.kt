// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.integration

import android.view.KeyEvent
import com.qtekfun.ultimateterminal.data.backup.BytesSource
import com.qtekfun.ultimateterminal.data.backup.Device
import com.qtekfun.ultimateterminal.data.backup.MemorySink
import com.qtekfun.ultimateterminal.data.backup.value
import com.qtekfun.ultimateterminal.data.storage.FileTrees
import com.qtekfun.ultimateterminal.data.storage.OwnerAccess
import com.qtekfun.ultimateterminal.data.storage.TreeVisitor
import com.qtekfun.ultimateterminal.data.storage.TreeWalker
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.ExtraKeyStyle
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.distro.InstallRequest
import com.qtekfun.ultimateterminal.domain.distro.InstallResult
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.session.SidebarMode
import com.qtekfun.ultimateterminal.domain.terminal.AppShortcut
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.KeyChord
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * An old device installs two distros and is configured; a backup of everything is exported (plain
 * and encrypted) and restored on an empty second device. What arrives is compared with what left,
 * field by field: the files of each distro, which one is the default and its user, the profiles,
 * the layouts, the shortcuts, the extra keys and the appearance.
 */
class BackupRoundTripIntegrationTest {
    @TempDir
    lateinit var dir: File
    private lateinit var old: Device
    private lateinit var new: Device
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() {
        old = Device(File(dir, "old"))
        new = Device(File(dir, "new"))
        server.start()
    }

    @AfterEach
    fun tearDown() {
        old.close()
        new.close()
        runCatching { server.close() }
    }

    private suspend fun install(name: String, user: String, marker: String): Distro {
        val archive = alpineTarGz(marker)
        server.enqueue(MockResponse.Builder().body(Buffer().write(archive)).build())
        val installer = old.installerFor(server.url("/$marker.tar.gz").toString(), archive)
        return (
            installer.install(
                InstallRequest(DistroFamily.ALPINE, name, user)
            ) as InstallResult.Success
            ).distro
    }

    private val shortcuts = ShortcutMap.defaults()
        .unbind(KeyChord(KeyEvent.KEYCODE_T, ctrl = true, shift = true))
        .bind(KeyChord(KeyEvent.KEYCODE_K, ctrl = true, alt = true), AppShortcut.NewTab)

    /** Two distros (the second the default, with another user) and every kind of configuration. */
    private suspend fun populate() {
        val work = install("Work", "root", "one")
        val play = install("Play", "dev", "two")
        old.distros.setDefault(play.id)
        old.settings.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                oledBlack = true,
                dynamicColor = false,
                keepAwake = true,
                defaultScrollbackLines = 5_000,
                terminalFontSizeSp = 16f,
                prootCompatibilityMode = true,
                sidebarMode = SidebarMode.ALWAYS_EXPANDED,
                dnsFallbackServers = listOf("9.9.9.9"),
                shortcuts = shortcuts,
                extraKeys = ExtraKeysConfig(listOf(listOf("esc", "tab"), listOf("up")), false),
                appearance = TerminalAppearance(
                    marginDp = 10,
                    cursorShape = CursorShape.BAR,
                    cursorBlink = true,
                    extraKeyStyle = ExtraKeyStyle.CLASSIC
                )
            )
        }
        val build = (
            old.profiles.add(
                Profile(name = "Build", distroId = work.id, user = "root", startupCommand = "make")
            ) as Outcome.Success
            ).value
        val free = (
            old.profiles.add(
                Profile(name = "Free", fontSizeSp = 20)
            ) as Outcome.Success
            ).value
        old.layouts.add(
            Layout(
                name = "Dev",
                root = LayoutNode.Split(
                    SplitOrientation.VERTICAL,
                    0.4f,
                    LayoutNode.Pane(profileId = build.id, command = "htop"),
                    LayoutNode.Pane(profileId = free.id)
                )
            )
        )
    }

    private suspend fun export(password: String?): ByteArray {
        val sink = MemorySink()
        old.exporter().export(ExportRequest(BackupKind.ALL, password = password), sink).value()
        return sink.bytes()
    }

    private suspend fun assertSameState() {
        val before = old.distros.observeAll().first().associateBy { it.name }
        val after = new.distros.observeAll().first().associateBy { it.name }
        assertEquals(before.keys, after.keys)
        for ((name, distro) in before) {
            val restored = after.getValue(name)
            assertEquals(distro.type, restored.type, name)
            assertEquals(distro.release, restored.release, name)
            assertEquals(distro.defaultUser, restored.defaultUser, name)
            assertEquals(distro.isDefault, restored.isDefault, name)
            assertEquals(distro.state, restored.state, name)
            assertEquals(treeOf(old.distroDir(distro)), treeOf(new.distroDir(restored)), name)
        }
        assertEquals("Play", new.distros.getDefault()?.name)
        assertEquals("dev", new.distros.getDefault()?.defaultUser)

        assertEquals(old.settings.observe().first(), new.settings.observe().first())

        assertEquals(portableConfig(old), portableConfig(new))
    }

    /**
     * Profiles and layouts as text, with database ids replaced by the names they stand for: ids
     * differ on another device, what they point at must not.
     */
    private suspend fun portableConfig(device: Device): List<String> {
        val distros = device.distros.observeAll().first().associate { it.id to it.name }
        val profiles = device.profiles.observeAll().first()
        val names = profiles.associate { it.id to it.name }
        fun LayoutNode.describe(): String = when (this) {
            is LayoutNode.Pane -> "pane(${profileId?.let(names::get)},$command)"

            is LayoutNode.Split ->
                "split($orientation,$ratio,${first.describe()},${second.describe()})"
        }
        return profiles.sortedBy { it.name }.map {
            it.copy(id = 0, distroId = null).toString() + " in " + distros[it.distroId]
        } + device.layouts.observeAll().first().map { it.name + " " + it.root.describe() }
    }

    @Test
    fun aPlainBackupOfEverythingRestoresOnAFreshDevice() = runBlocking {
        populate()

        val bytes = export(password = null)
        val summary = new.restorer().restore(BytesSource(bytes)).value()

        assertEquals(2, summary.distros)
        assertEquals(2, summary.profiles)
        assertEquals(1, summary.layouts)
        assertEquals(0, summary.skipped)
        assertSameState()
    }

    @Test
    fun anEncryptedBackupRestoresTheSameAndHidesItsContent() = runBlocking {
        populate()

        val bytes = export(password = "correct horse")
        val plain = export(password = null)

        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Play"))
        assertTrue(String(plain, Charsets.ISO_8859_1).contains("Play"))
        assertTrue(new.restorer().probe(BytesSource(bytes)).value().encrypted)
        new.restorer().restore(BytesSource(bytes), "correct horse").value()
        assertSameState()
    }

    @Test
    fun aWrongPasswordRestoresNothing() = runBlocking {
        populate()
        val bytes = export(password = "right")

        val result = new.restorer().restore(BytesSource(bytes), "wrong")

        assertTrue(result is com.qtekfun.ultimateterminal.domain.backup.BackupResult.Failure)
        assertEquals(emptyList<Any>(), new.distros.observeAll().first())
        assertEquals(emptyList<Any>(), new.profiles.observeAll().first())
        assertEquals(
            emptyList<String>(),
            File(new.storageRoot, "distros").list().orEmpty().toList()
        )
    }

    /** Mode bits and link targets of everything under [root], to compare two trees exactly. */
    private fun modesOf(root: File): Map<String, String> {
        val base = root.toPath()
        val found = LinkedHashMap<String, String>()
        TreeWalker.walk(
            base,
            object : TreeVisitor {
                override fun enterDirectory(dir: Path, attrs: BasicFileAttributes) {
                    record(dir)
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes) = record(file)

                private fun record(path: Path) {
                    val key = base.relativize(path).toString()
                    found[key] = if (Files.isSymbolicLink(path)) {
                        "link " + Files.readSymbolicLink(path)
                    } else {
                        "mode " + Integer.toOctalString(OwnerAccess.modeOf(path) and 0b111_111_111)
                    }
                }
            }
        )
        return found
    }

    private fun closeForEveryone(file: File) {
        OwnerAccess.setMode(file.toPath(), 0)
    }

    @Test
    fun filesOnlyRootCanReadTravelWithTheirModesToADeviceWithAnotherBasePath() = runBlocking {
        assumeFalse(Files.getAttribute(dir.toPath(), "unix:uid") == 0)
        populate()
        val play = old.distros.observeAll().first().first { it.name == "Play" }
        val root = old.distroDir(play)
        File(root, "etc").mkdirs()
        File(root, "usr/bin").mkdirs()
        File(root, "etc/shadow").writeText("root:*:0")
        File(root, "usr/bin/sudo").writeText("ELF")
        File(root, "locked").mkdirs()
        File(root, "locked/key").writeText("k")
        // 2 MiB of bytes that gzip cannot shrink, so the part is really streamed.
        val big = ByteArray(2 * 1024 * 1024).also { java.util.Random(7).nextBytes(it) }
        File(root, "big.bin").writeBytes(big)
        Files.createSymbolicLink(File(root, "etc/abs").toPath(), Paths.get("/etc/missing.so"))
        Files.createSymbolicLink(File(root, "etc/rel").toPath(), Paths.get("../usr/bin/sudo"))
        Files.createLink(File(root, "etc/shadow-").toPath(), File(root, "etc/shadow").toPath())
        closeForEveryone(File(root, "etc/shadow"))
        OwnerAccess.setMode(File(root, "usr/bin/sudo").toPath(), 0b001_001_001)
        closeForEveryone(File(root, "locked/key"))
        closeForEveryone(File(root, "locked"))

        val bytes = export(password = "pw")
        val oldBase = old.storageRoot.path
        new.restorer().restore(BytesSource(bytes), "pw").value()

        val restored = new.distros.observeAll().first().first { it.name == "Play" }
        val copy = new.distroDir(restored)
        // Another base path, and a fresh directory name: nothing of the old device is reused.
        assertTrue(new.storageRoot.path != oldBase)
        assertTrue(restored.directory != play.directory)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(oldBase))
        // Everything except the one directory the owner needs open comes back with the same mode.
        val before = modesOf(root)
        val after = modesOf(copy)
        assertEquals(before - "locked", after - "locked")
        assertEquals("mode 0", before["locked/key"])
        assertEquals("mode 0", after["locked/key"])
        assertEquals("mode 0", after["etc/shadow"])
        assertEquals("mode 0", after["etc/shadow-"]) // a hard link comes back as its own file
        assertEquals("mode 111", after["usr/bin/sudo"])
        assertEquals("link /etc/missing.so", after["etc/abs"])
        assertEquals("link ../usr/bin/sudo", after["etc/rel"])
        assertEquals("mode 700", after["locked"])
        File(copy, "etc/shadow").setReadable(true, true)
        assertEquals("root:*:0", File(copy, "etc/shadow").readText())
        assertTrue(big.contentEquals(File(copy, "big.bin").readBytes()))
        // The source device was left exactly as it was (closed files stay closed).
        assertEquals(0, OwnerAccess.modeOf(File(root, "locked").toPath()))
        FileTrees.delete(root.toPath())
        FileTrees.delete(copy.toPath())
    }

    @Test
    fun aBackupCutShortOrChangedOnTheWayLeavesTheNewDeviceEmpty() = runBlocking {
        populate()
        val bytes = export(password = null)

        for (damaged in listOf(bytes.copyOf(bytes.size * 6 / 10), bytes.copyOf(bytes.size / 3))) {
            val result = new.restorer().restore(BytesSource(damaged))
            assertTrue(result is BackupResult.Failure)
            assertEquals(emptyList<Any>(), new.distros.observeAll().first())
            assertEquals(emptyList<Any>(), new.profiles.observeAll().first())
            assertEquals(ThemeMode.SYSTEM, new.settings.observe().first().themeMode)
            assertEquals(
                emptyList<String>(),
                File(new.storageRoot, "distros").list().orEmpty().toList()
            )
            assertEquals(
                emptyList<String>(),
                File(new.storageRoot, "distros-tmp").list().orEmpty().toList()
            )
        }
    }
}
