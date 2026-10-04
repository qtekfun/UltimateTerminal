// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupManifestTest {
    private val hash = "a".repeat(64)
    private val meta = DistroMeta("Alpine", "ALPINE", "3.20", "root", true)
    private val config = ManifestPart(PartNames.CONFIG, hash, 10)
    private fun distro(index: Int = 0, meta: DistroMeta? = this.meta) =
        ManifestPart(PartNames.distro(index), hash, 100, meta)

    private fun manifest(
        kind: String,
        parts: List<ManifestPart>,
        format: Int = ManifestCodec.FORMAT
    ) = BackupManifest(format, "0.1.0", kind, "2026-10-04T12:00:00Z", parts)

    private fun decode(manifest: BackupManifest) =
        ManifestCodec.decode(ManifestCodec.encode(manifest))

    private fun reason(manifest: BackupManifest): String =
        ((decode(manifest) as BackupResult.Failure).error as BackupError.InvalidManifest).reason

    @Test
    fun everyKindOfBackupHasItsOwnValidShape() {
        assertEquals(
            BackupKind.CONFIG,
            decode(manifest("CONFIG", listOf(config))).value().backupKind
        )
        assertEquals(
            BackupKind.DISTRO,
            decode(manifest("DISTRO", listOf(distro()))).value().backupKind
        )
        assertEquals(
            BackupKind.ALL,
            decode(manifest("ALL", listOf(config, distro(0), distro(1)))).value().backupKind
        )
        assertEquals(BackupKind.ALL, decode(manifest("ALL", listOf(config))).value().backupKind)
    }

    @Test
    fun aManifestSurvivesEncodingAndDecoding() {
        val original = manifest("ALL", listOf(config, distro()))
        assertEquals(original, decode(original).value())
    }

    @Test
    fun somethingThatIsNotAManifestIsInvalid() {
        for (text in listOf("", "hello", "[]", "{\"format\": 1}")) {
            val result = ManifestCodec.decode(text.toByteArray())
            assertTrue(result.errorOrNull() is BackupError.InvalidManifest, text)
        }
    }

    @Test
    fun aFormatThatIsNotTheCurrentOneIsUnsupported() {
        assertEquals(
            BackupError.UnsupportedVersion(2),
            decode(manifest("CONFIG", listOf(config), format = 2)).errorOrNull()
        )
        assertEquals(
            BackupError.UnsupportedVersion(0),
            decode(manifest("CONFIG", listOf(config), format = 0)).errorOrNull()
        )
    }

    @Test
    fun inconsistentManifestsAreRefusedWithAReason() {
        assertEquals("unknown kind", reason(manifest("EVERYTHING", listOf(config))))
        assertEquals("a part is listed twice", reason(manifest("ALL", listOf(config, config))))
        assertEquals("unknown part", reason(manifest("ALL", listOf(ManifestPart("../x", hash, 1)))))
        assertEquals(
            "unknown part",
            reason(manifest("ALL", listOf(ManifestPart("manifest.json", hash, 1))))
        )
        assertEquals(
            "bad hash or size",
            reason(manifest("ALL", listOf(ManifestPart(PartNames.CONFIG, "ABC", 1))))
        )
        assertEquals(
            "bad hash or size",
            reason(manifest("CONFIG", listOf(ManifestPart(PartNames.CONFIG, hash.uppercase(), 1))))
        )
        assertEquals(
            "bad hash or size",
            reason(manifest("CONFIG", listOf(ManifestPart(PartNames.CONFIG, hash, -1))))
        )
        assertEquals(
            "too many distros",
            reason(
                manifest(
                    "ALL",
                    listOf(config) + (0..1000).map { distro(it % 10_000) }
                )
            )
        )
        assertEquals(
            "bad distro description",
            reason(manifest("DISTRO", listOf(distro(meta = null))))
        )
        assertEquals(
            "bad distro description",
            reason(manifest("DISTRO", listOf(distro(meta = meta.copy(type = "GENTOO")))))
        )
        assertEquals(
            "the configuration is not a distro",
            reason(manifest("CONFIG", listOf(ManifestPart(PartNames.CONFIG, hash, 1, meta))))
        )
    }

    @Test
    fun eachKindRefusesTheWrongParts() {
        assertEquals(
            "a configuration backup holds only the configuration",
            reason(manifest("CONFIG", emptyList()))
        )
        assertEquals(
            "a configuration backup holds only the configuration",
            reason(manifest("CONFIG", listOf(config, distro())))
        )
        assertEquals(
            "a distro backup holds exactly one distro",
            reason(manifest("DISTRO", listOf(config, distro())))
        )
        assertEquals(
            "a distro backup holds exactly one distro",
            reason(manifest("DISTRO", emptyList()))
        )
        assertEquals(
            "a distro backup holds exactly one distro",
            reason(manifest("DISTRO", listOf(distro(0), distro(1))))
        )
        assertEquals(
            "a full backup holds the configuration",
            reason(manifest("ALL", listOf(distro())))
        )
    }

    @Test
    fun partNamesAreRecognizedStrictly() {
        assertTrue(PartNames.isKnown("manifest.json"))
        assertTrue(PartNames.isKnown("config.json"))
        assertTrue(PartNames.isKnown("distros/0.tar.gz"))
        assertTrue(PartNames.isKnown("distros/9999.tar.gz"))
        for (name in listOf(
            "distros/10000.tar.gz",
            "distros/a.tar.gz",
            "distros/../x",
            "/config.json",
            "config.json/",
            "x"
        )) {
            assertFalse(PartNames.isKnown(name), name)
        }
        assertFalse(PartNames.isDistro("config.json"))
        assertEquals("distros/3.tar.gz", PartNames.distro(3))
    }
}
