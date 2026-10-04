// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConfigRestoreTest {
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

    private fun apply(snapshot: ConfigSnapshot, on: Device = new) = runBlocking {
        on.restorer().restore(BytesSource(BackupBuilder("CONFIG").config(snapshot).build())).value()
    }

    private val custom = BuiltInSchemes.nord.copy(
        id = "custom-mine",
        name = "Mine",
        builtIn = false
    )

    private suspend fun fill(device: Device) {
        val alpine = device.addDistro("Alpine")
        device.distros.setDefault(alpine.id)
        fillSettings(device)
        fillItems(device, alpine.id)
    }

    private suspend fun fillSettings(device: Device) {
        device.settings.update {
            it.copy(
                themeMode = ThemeMode.LIGHT,
                oledBlack = true,
                dynamicColor = false,
                keepAwake = true,
                sharedStorage = true,
                defaultScrollbackLines = 2_000,
                terminalSchemeId = "custom-mine",
                terminalFontSizeSp = 18f,
                customSchemes = listOf(custom),
                prootCompatibilityMode = true,
                dnsFallbackServers = listOf("9.9.9.9", "149.112.112.112"),
                extraKeys = ExtraKeysConfig(
                    listOf(listOf("esc", "ctrl"), listOf("up")),
                    onlyWithKeyboard = false
                ),
                appearance = TerminalAppearance(
                    marginDp = 12,
                    cursorShape = CursorShape.BAR,
                    cursorBlink = true
                )
            )
        }
    }

    private suspend fun fillItems(device: Device, alpineId: Long) {
        val first = (
            device.profiles.add(
                Profile(name = "Work", distroId = alpineId, startupCommand = "tmux")
            ) as Outcome.Success
            ).value
        val second = (
            device.profiles.add(
                Profile(name = "Play", fontSizeSp = 20)
            ) as Outcome.Success
            ).value
        device.layouts.add(
            com.qtekfun.ultimateterminal.domain.model.Layout(
                name = "Two",
                root = LayoutNode.Split(
                    SplitOrientation.VERTICAL,
                    0.3f,
                    LayoutNode.Pane(profileId = second.id, command = "htop"),
                    LayoutNode.Split(
                        SplitOrientation.HORIZONTAL,
                        0.5f,
                        LayoutNode.Pane(profileId = first.id),
                        LayoutNode.Pane()
                    )
                )
            )
        )
        device.keys.put(
            SshKeyInfo(
                "k000000000001",
                "laptop",
                SshKeyType.ED25519,
                "ssh-ed25519 AAAA",
                "SHA256:x",
                Instant.parse("2026-10-01T10:00:00Z")
            ),
            "PRIVATE-1"
        )
        device.hosts.add(
            SshHost(
                name = "web",
                host = "example.org",
                port = 2222,
                user = "admin",
                keyAlias = "k000000000001",
                distroId = alpineId
            )
        )
    }

    @Test
    fun theWholeConfigurationArrivesOnAnotherDevice() = runBlocking {
        fill(old)
        new.addDistro("alpine") // the same distro is already there, under another case
        val bytes = old.exportBytes(ExportRequest(BackupKind.CONFIG, password = "pw"))

        val summary = new.restorer().restore(BytesSource(bytes), "pw").value()

        assertEquals(2, summary.profiles)
        assertEquals(1, summary.layouts)
        assertEquals(1, summary.sshHosts)
        assertEquals(1, summary.sshKeys)
        assertEquals(0, summary.skipped)
        val settings = new.settings.observe().first()
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertTrue(settings.oledBlack)
        assertFalse(settings.dynamicColor)
        assertTrue(settings.keepAwake)
        assertFalse(settings.sharedStorage) // the permission belongs to the device
        assertEquals(2_000, settings.defaultScrollbackLines)
        assertEquals("custom-mine", settings.terminalSchemeId)
        assertEquals(18f, settings.terminalFontSizeSp)
        assertEquals(listOf(custom), settings.customSchemes)
        assertTrue(settings.prootCompatibilityMode)
        assertEquals(listOf("9.9.9.9", "149.112.112.112"), settings.dnsFallbackServers)
        assertEquals(listOf(listOf("esc", "ctrl"), listOf("up")), settings.extraKeys.rows)
        assertFalse(settings.extraKeys.onlyWithKeyboard)
        assertEquals(12, settings.appearance.marginDp)
        assertEquals(CursorShape.BAR, settings.appearance.cursorShape)
        assertTrue(settings.appearance.cursorBlink)
        val distro = new.distros.observeAll().first().single()
        assertTrue(distro.isDefault)
        val profiles = new.profiles.observeAll().first().associateBy { it.name }
        assertEquals(distro.id, profiles.getValue("Work").distroId)
        assertEquals("tmux", profiles.getValue("Work").startupCommand)
        val layout = new.layouts.observeAll().first().single().root as LayoutNode.Split
        assertEquals(profiles.getValue("Play").id, (layout.first as LayoutNode.Pane).profileId)
        val inner = layout.second as LayoutNode.Split
        assertEquals(profiles.getValue("Work").id, (inner.first as LayoutNode.Pane).profileId)
        assertNull((inner.second as LayoutNode.Pane).profileId)
        val host = new.hosts.observeAll().first().single()
        assertEquals("k000000000001", host.keyAlias)
        assertEquals(distro.id, host.distroId)
        assertEquals(
            "PRIVATE-1",
            (
                new.keys.privateKey(
                    "k000000000001"
                ) as com.qtekfun.ultimateterminal.domain.ssh.SshResult.Success
                ).value
        )
    }

    @Test
    fun restoringTheSameConfigurationAgainOnlySkips() = runBlocking {
        fill(old)
        val bytes = old.exportBytes(ExportRequest(BackupKind.CONFIG, password = "pw"))
        new.restorer().restore(BytesSource(bytes), "pw").value()
        val again = new.restorer().restore(BytesSource(bytes), "pw").value()
        assertEquals(0, again.profiles + again.layouts + again.sshHosts + again.sshKeys)
        assertEquals(5, again.skipped)
        assertEquals(2, new.profiles.observeAll().first().size)
        assertEquals(1, new.layouts.observeAll().first().size)
    }

    @Test
    fun aKeyLeftOutMakesTheHostForgetItsAliasButKeepTheRest() = runBlocking {
        fill(old)
        new.addDistro("Alpine")
        val bytes = old.exportBytes(ExportRequest(BackupKind.CONFIG, includeSshKeys = false))
        val summary = new.restorer().restore(BytesSource(bytes)).value()
        assertEquals(0, summary.sshKeys)
        assertNull(new.hosts.observeAll().first().single().keyAlias)
    }

    @Test
    fun withoutTheDistroOnThisDeviceReferencesBecomeTheDefault() = runBlocking {
        fill(old)
        val bytes = old.exportBytes(ExportRequest(BackupKind.CONFIG, password = "pw"))
        new.restorer().restore(BytesSource(bytes), "pw").value()
        assertNull(new.profiles.observeAll().first().first { it.name == "Work" }.distroId)
        assertNull(new.hosts.observeAll().first().single().distroId)
        assertEquals(emptyList<Any>(), new.distros.observeAll().first())
    }

    @Test
    fun valuesOutOfRangeFallBackToSafeOnes() = runBlocking {
        val settings = sampleSettings().copy(
            themeMode = "BLUE",
            defaultScrollbackLines = 5,
            terminalSchemeId = "nope",
            terminalFontSizeSp = 1_000f,
            customSchemes = "not json"
        )
        val summary = apply(sampleSnapshot().copy(settings = settings, distros = emptyList()))
        assertTrue(summary.settingsApplied)
        val now = new.settings.observe().first()
        assertEquals(ThemeMode.SYSTEM, now.themeMode)
        assertEquals(Profile.SCROLLBACK_RANGE.first, now.defaultScrollbackLines)
        assertEquals(BuiltInSchemes.DEFAULT_ID, now.terminalSchemeId)
        assertEquals(FontZoom.MAX_SP, now.terminalFontSizeSp)
        assertEquals(emptyList<Any>(), now.customSchemes)
        val small =
            apply(
                sampleSnapshot().copy(
                    settings = settings.copy(
                        terminalFontSizeSp = 0.5f,
                        customSchemes = SchemeCodec.encodeList(listOf(custom)),
                        terminalSchemeId = "custom-mine"
                    )
                )
            )
        assertTrue(small.settingsApplied)
        assertEquals(FontZoom.MIN_SP, new.settings.observe().first().terminalFontSizeSp)
        assertEquals("custom-mine", new.settings.observe().first().terminalSchemeId)
    }

    @Test
    fun itemsThatCannotBeAddedAreSkippedNotFatal() = runBlocking {
        val badKeys = listOf(
            sampleSnapshot().sshKeys.single().copy(alias = "k000000000002", type = "DSA"),
            sampleSnapshot().sshKeys.single().copy(alias = "k000000000003", createdAt = "yesterday")
        )
        val snapshot = sampleSnapshot().copy(
            sshKeys = badKeys + sampleSnapshot().sshKeys,
            profiles = listOf(
                ProfileDto("  ", "nord", "monospace", 14, 10_000),
                sampleSnapshot().profiles.single()
            ),
            layouts = listOf(
                LayoutDto(" ", LayoutNode.Pane()),
                LayoutDto("Out of range", LayoutNode.Pane(profileId = 7)),
                LayoutDto("Pointing at the bad one", LayoutNode.Pane(profileId = 0))
            ),
            sshHosts = listOf(
                HostDto("bad", "", 22, "root"),
                HostDto("ok", "example.org", 22, "root", keyAlias = "k-unknown")
            )
        )
        new.keys.failPuts = false
        val summary = apply(snapshot)
        assertEquals(1, summary.sshKeys)
        assertEquals(1, summary.profiles)
        assertEquals(2, summary.layouts)
        assertEquals(1, summary.sshHosts)
        assertEquals(2 + 1 + 1 + 1, summary.skipped)
        val layouts = new.layouts.observeAll().first().associate { it.name to it.root }
        assertNull((layouts.getValue("Out of range") as LayoutNode.Pane).profileId)
        assertNull((layouts.getValue("Pointing at the bad one") as LayoutNode.Pane).profileId)
        assertNull(new.hosts.observeAll().first().single().keyAlias)
    }

    @Test
    fun aKeyStoreThatRefusesIsSkippedNotFatal() = runBlocking {
        new.keys.failPuts = true
        val summary = apply(sampleSnapshot())
        assertEquals(0, summary.sshKeys)
        assertTrue(summary.skipped >= 1)
    }

    @Test
    fun anExistingItemWithTheSameNameIsKeptAndReferencedByLayouts() = runBlocking {
        val mine = (
            new.profiles.add(
                Profile(name = "WORK", fontSizeSp = 30)
            ) as Outcome.Success
            ).value
        new.layouts.add(
            com.qtekfun.ultimateterminal.domain.model.Layout(
                name = "TWO PANES",
                root = LayoutNode.Pane()
            )
        )
        new.hosts.add(SshHost(name = "WEB", host = "mine.example", user = "me"))
        new.keys.put(
            SshKeyInfo(
                "k000000000001",
                "mine",
                SshKeyType.RSA,
                "ssh-rsa BBBB",
                "SHA256:y",
                Instant.EPOCH
            ),
            "MY-PRIVATE"
        )
        val snapshot = sampleSnapshot().copy(
            profiles = listOf(ProfileDto("Work", "nord", "monospace", 14, 10_000)),
            layouts = listOf(
                LayoutDto("Two panes", LayoutNode.Pane()),
                LayoutDto("Other", LayoutNode.Pane(profileId = 0))
            )
        )
        val summary = apply(snapshot)
        assertEquals(0, summary.profiles)
        assertEquals(1, summary.layouts)
        assertEquals(0, summary.sshHosts)
        assertEquals(0, summary.sshKeys)
        assertEquals(30, new.profiles.get(mine.id)!!.fontSizeSp)
        assertEquals("mine.example", new.hosts.observeAll().first().single().host)
        val other = new.layouts.observeAll().first().first { it.name == "Other" }
        assertEquals(mine.id, (other.root as LayoutNode.Pane).profileId)
        assertEquals(
            "MY-PRIVATE",
            (
                new.keys.privateKey(
                    "k000000000001"
                ) as com.qtekfun.ultimateterminal.domain.ssh.SshResult.Success
                ).value
        )
    }

    @Test
    fun aRestoredDistroTakesPriorityOverASameNamedOneForReferences() = runBlocking {
        val existing = new.addDistro("Alpine")
        val bytes = BackupBuilder("ALL")
            .config(sampleSnapshot().copy(distros = listOf(DistroRefDto("Alpine", true))))
            .distro(0, BackupBuilder.rootfs("etc/a" to "one"), name = "Alpine")
            .build()
        new.restorer().restore(BytesSource(bytes)).value()
        val restored = new.distros.observeAll().first().first { it.id != existing.id }
        assertEquals(restored.id, new.profiles.observeAll().first().single().distroId)
        assertTrue(restored.isDefault)
    }
}
