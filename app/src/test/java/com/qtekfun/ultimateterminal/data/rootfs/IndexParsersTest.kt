// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IndexParsersTest {
    @Test
    fun alpineReadsTheMinirootfsEntryAndIgnoresTheOthers() {
        val source = IndexParsers.alpine(
            Samples.ALPINE_YAML,
            Architecture.ARM64,
            "https://cdn/releases/aarch64"
        )
        assertEquals(
            RootfsSource(
                DistroFamily.ALPINE,
                Architecture.ARM64,
                "3.24.2",
                "https://cdn/releases/aarch64/alpine-minirootfs-3.24.2-aarch64.tar.gz",
                Samples.ALPINE_SHA,
                4_028_030
            ),
            source
        )
    }

    @Test
    fun alpineWithoutAMinirootfsEntryHasNoSource() {
        val netbootOnly = "-\n  title: \"Netboot\"\n  flavor: alpine-netboot\n"
        assertNull(IndexParsers.alpine(netbootOnly, Architecture.ARM64, "https://cdn"))
        assertNull(IndexParsers.alpine("", Architecture.ARM64, "https://cdn"))
        assertNull(
            IndexParsers.alpine("  flavor: alpine-minirootfs\n", Architecture.ARM64, "https://cdn")
        )
    }

    @Test
    fun alpineEntryMissingAFieldHasNoSource() {
        for (field in listOf("file", "version", "sha256", "size")) {
            val yaml = Samples.ALPINE_YAML.lines().filterNot {
                it.startsWith("  $field: ")
            }.joinToString("\n")
            assertNull(IndexParsers.alpine(yaml, Architecture.ARM64, "https://cdn"), field)
        }
    }

    @Test
    fun alpineEntryWithANonNumericSizeHasNoSource() {
        val yaml = Samples.ALPINE_YAML.replace("size: 4028030", "size: big")
        assertNull(IndexParsers.alpine(yaml, Architecture.ARM64, "https://cdn"))
    }

    @Test
    fun ubuntuPicksTheHighestVersionForTheArchitectureComparingNumbers() {
        val source = IndexParsers.ubuntu(
            Samples.UBUNTU_SUMS,
            Architecture.ARM64,
            "arm64",
            "https://u/release"
        )
        assertEquals(
            RootfsSource(
                DistroFamily.UBUNTU,
                Architecture.ARM64,
                "24.04.10",
                "https://u/release/ubuntu-base-24.04.10-base-arm64.tar.gz",
                "1".repeat(64),
                null
            ),
            source
        )
    }

    @Test
    fun ubuntuUsesOnlyLinesOfTheRequestedArchitecture() {
        val source = IndexParsers.ubuntu(
            Samples.UBUNTU_SUMS,
            Architecture.ARMV7,
            "armhf",
            "https://u/release"
        )
        assertEquals("24.04.4", source?.version)
        assertEquals("https://u/release/ubuntu-base-24.04.4-base-armhf.tar.gz", source?.url)
    }

    @Test
    fun ubuntuComparesVersionsOfDifferentLengthAndRepeatedOnes() {
        val sums = listOf("24.04", "24.04.1", "24.04.1").mapIndexed { i, v ->
            "${i.toString().repeat(64)} *ubuntu-base-$v-base-amd64.tar.gz"
        }.joinToString("\n")
        assertEquals(
            "24.04.1",
            IndexParsers.ubuntu(sums, Architecture.X86_64, "amd64", "https://u")?.version
        )
        val dotted = listOf("24..2" to "a", "24.1" to "b")
            .joinToString("\n") { (v, c) -> "${c.repeat(64)} *ubuntu-base-$v-base-amd64.tar.gz" }
        assertEquals(
            "24.1",
            IndexParsers.ubuntu(dotted, Architecture.X86_64, "amd64", "https://u")?.version
        )
    }

    @Test
    fun ubuntuWithNoMatchingLineHasNoSource() {
        assertNull(
            IndexParsers.ubuntu(Samples.UBUNTU_SUMS, Architecture.X86_64, "s390x", "https://u")
        )
        assertNull(
            IndexParsers.ubuntu("not a checksum file", Architecture.ARM64, "arm64", "https://u")
        )
    }

    @Test
    fun debianReadsTheSingleLayerOfTheOciManifest() {
        val source = IndexParsers.debian(
            Samples.DEBIAN_MANIFEST,
            Architecture.ARM64,
            "trixie",
            "https://d/rootfs.tar.gz"
        )
        assertEquals(
            RootfsSource(
                DistroFamily.DEBIAN,
                Architecture.ARM64,
                "trixie",
                "https://d/rootfs.tar.gz",
                Samples.DEBIAN_SHA,
                30_189_691
            ),
            source
        )
    }

    @Test
    fun debianManifestThatIsNotUsableHasNoSource() {
        val url = "https://d/rootfs.tar.gz"
        val layers = """{"digest":"sha256:aa","size":1},{"digest":"sha256:bb","size":2}"""
        val twoLayers = """{"layers":[$layers]}"""
        val md5 = """{"layers":[{"digest":"md5:aa","size":1}]}"""
        for (bad in listOf("not json", "{}", """{"layers":[]}""", twoLayers, md5)) {
            assertNull(IndexParsers.debian(bad, Architecture.ARM64, "trixie", url), bad)
        }
    }

    @Test
    fun fedoraReleasesAreListedNewestFirstAndIgnoreOtherLinks() {
        assertEquals(listOf(45, 44, 43, 42), IndexParsers.fedoraVersions(Samples.FEDORA_RELEASES))
        assertEquals(emptyList<Int>(), IndexParsers.fedoraVersions("<a href=\"test/\">test/</a>"))
    }

    @Test
    fun fedoraTakesTheBaseImageAndNotTheMinimalOrToolboxOnes() {
        val image = IndexParsers.fedoraImage(Samples.FEDORA_IMAGES, 44, "aarch64")

        assertEquals(
            IndexParsers.FedoraImage(
                "Fedora-Container-Base-Generic-44-1.7.aarch64.oci.tar.xz",
                "Fedora-Container-44-1.7-aarch64-CHECKSUM"
            ),
            image
        )
    }

    @Test
    fun fedoraImageIsNullForAnotherReleaseArchitectureOrWithoutItsChecksum() {
        assertNull(IndexParsers.fedoraImage(Samples.FEDORA_IMAGES, 43, "aarch64"))
        assertNull(IndexParsers.fedoraImage(Samples.FEDORA_IMAGES, 44, "x86_64"))
        val noChecksum = Samples.FEDORA_IMAGES.lines().filterNot {
            "CHECKSUM" in it
        }.joinToString("\n")
        assertNull(IndexParsers.fedoraImage(noChecksum, 44, "aarch64"))
        assertNull(IndexParsers.fedoraImage("", 44, "aarch64"))
    }

    @Test
    fun fedoraChecksumReadsTheHashAndSizeOfTheNamedFileOnly() {
        val base = "Fedora-Container-Base-Generic-44-1.7.aarch64.oci.tar.xz"
        val minimal = "Fedora-Container-Base-Generic-Minimal-44-1.7.aarch64.oci.tar.xz"

        assertEquals(
            IndexParsers.FedoraChecksum(Samples.FEDORA_SHA, Samples.FEDORA_SIZE),
            IndexParsers.fedoraChecksum(Samples.FEDORA_CHECKSUM, base)
        )
        assertEquals(
            IndexParsers.FedoraChecksum(
                "2c00fc0e7890a5bfecbd243561e5a2d07d2661667e1b897eab549b83f6b1db9a",
                51_427_176L
            ),
            IndexParsers.fedoraChecksum(Samples.FEDORA_CHECKSUM, minimal)
        )
    }

    @Test
    fun fedoraChecksumIsNullWithoutALineForTheFileOrWithAShortHash() {
        val base = "Fedora-Container-Base-Generic-44-1.7.aarch64.oci.tar.xz"

        assertNull(IndexParsers.fedoraChecksum(Samples.FEDORA_CHECKSUM, "other.tar.xz"))
        assertNull(IndexParsers.fedoraChecksum("SHA256 ($base) = abc123", base))
        // A file with no size line still has its hash.
        assertEquals(
            IndexParsers.FedoraChecksum(Samples.FEDORA_SHA, null),
            IndexParsers.fedoraChecksum("SHA256 ($base) = ${Samples.FEDORA_SHA.uppercase()}", base)
        )
    }
}
