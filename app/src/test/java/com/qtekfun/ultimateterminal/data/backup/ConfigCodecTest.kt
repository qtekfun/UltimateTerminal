// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import com.qtekfun.ultimateterminal.domain.model.SplitOrientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal fun sampleSettings() = SettingsDto(
    themeMode = "DARK",
    oledBlack = true,
    dynamicColor = false,
    keepAwake = true,
    sharedStorage = true,
    defaultScrollbackLines = 5_000,
    terminalSchemeId = "nord",
    terminalFontSizeSp = 16f,
    customSchemes = "[]"
)

internal fun sampleSnapshot() = ConfigSnapshot(
    settings = sampleSettings(),
    distros = listOf(DistroRefDto("Alpine", true)),
    profiles = listOf(
        ProfileDto("Work", "nord", "monospace", 14, 10_000, "Alpine", "root", "tmux")
    ),
    layouts = listOf(
        LayoutDto(
            "Two panes",
            LayoutNode.Split(
                SplitOrientation.VERTICAL,
                0.4f,
                LayoutNode.Pane(profileId = 0, command = "htop"),
                LayoutNode.Pane()
            )
        )
    ),
    sshHosts = listOf(HostDto("web", "example.org", 2222, "admin", "k000000000001", "Alpine")),
    sshKeys = listOf(
        KeyDto(
            "k000000000001",
            "laptop",
            "ED25519",
            "ssh-ed25519 AAAA",
            "SHA256:abc",
            "2026-10-01T10:00:00Z",
            "-----BEGIN OPENSSH PRIVATE KEY-----"
        )
    )
)

class ConfigCodecTest {
    private fun text(bytes: ByteArray) = String(bytes, Charsets.UTF_8)

    @Test
    fun aSnapshotSurvivesEncodingAndDecoding() {
        val snapshot = sampleSnapshot()
        assertEquals(snapshot, ConfigCodec.decode(ConfigCodec.encode(snapshot)).value())
    }

    @Test
    fun unknownFieldsFromALaterVersionAreIgnored() {
        val json = text(
            ConfigCodec.encode(sampleSnapshot())
        ).replaceFirst("{", "{\"future\": [1, 2],")
        assertEquals(sampleSnapshot(), ConfigCodec.decode(json.toByteArray()).value())
    }

    @Test
    fun aMissingListMeansNoItems() {
        val json = """{"settings": ${text(
            ConfigCodec.encode(sampleSnapshot())
        ).substringAfter("\"settings\":").substringBefore(",\"distros\"")}}"""
        val decoded = ConfigCodec.decode(json.toByteArray()).value()
        assertEquals(emptyList<HostDto>(), decoded.sshHosts)
        assertEquals(sampleSettings(), decoded.settings)
    }

    @Test
    fun somethingThatIsNotAConfigurationIsInvalid() {
        for (junk in listOf("", "nope", "[]", "{}")) {
            assertEquals(
                BackupError.InvalidConfig("not a configuration"),
                ConfigCodec.decode(junk.toByteArray()).errorOrNull(),
                junk
            )
        }
    }

    @Test
    fun aLayoutWithARatioOutOfRangeIsInvalidNotACrash() {
        val json = text(ConfigCodec.encode(sampleSnapshot())).replace("0.4", "7.0")
        assertEquals(
            BackupError.InvalidConfig("not a configuration"),
            ConfigCodec.decode(json.toByteArray()).errorOrNull()
        )
    }

    @Test
    fun aNewerVersionIsNotGuessedAt() {
        val json = text(
            ConfigCodec.encode(sampleSnapshot())
        ).replace("\"version\":1", "\"version\":2")
        assertEquals(
            BackupError.UnsupportedVersion(2),
            ConfigCodec.decode(json.toByteArray()).errorOrNull()
        )
    }

    @Test
    fun profileReferencesInALayoutAreRewrittenEverywhere() {
        val tree = LayoutNode.Split(
            SplitOrientation.HORIZONTAL,
            0.5f,
            LayoutNode.Split(
                SplitOrientation.VERTICAL,
                0.5f,
                LayoutNode.Pane(1, "a"),
                LayoutNode.Pane(null)
            ),
            LayoutNode.Pane(2, "b")
        )
        val mapped = tree.mapProfiles { id -> id?.let { it * 10 } }
        val expected = LayoutNode.Split(
            SplitOrientation.HORIZONTAL,
            0.5f,
            LayoutNode.Split(
                SplitOrientation.VERTICAL,
                0.5f,
                LayoutNode.Pane(10, "a"),
                LayoutNode.Pane(null)
            ),
            LayoutNode.Pane(20, "b")
        )
        assertEquals(expected, mapped)
    }
}
