// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.data.rootfs.ExtractorFixtures.fedoraRootfs
import com.qtekfun.ultimateterminal.data.rootfs.ExtractorFixtures.layer
import com.qtekfun.ultimateterminal.data.rootfs.ExtractorFixtures.ociTar
import com.qtekfun.ultimateterminal.data.storage.NioFileSystemRepository
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import com.qtekfun.ultimateterminal.domain.distro.ExtractionResult
import com.qtekfun.ultimateterminal.domain.model.FsPath
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** xz and OCI archives, the formats of Fedora's image (T24). Hostile input gets the same refusals. */
class TarXzExtractorTest {
    @TempDir
    lateinit var dir: File

    private val storageRoot get() = File(dir, "storage").also { it.mkdirs() }
    private val fileSystem get() = NioFileSystemRepository(storageRoot.toPath(), Dispatchers.IO) {
        Long.MAX_VALUE
    }

    private fun path(raw: String) = (FsPath.of(raw) as Outcome.Success).value

    /** [name] is only a name: the format has to come from the bytes. */
    private fun unpack(
        archive: ByteArray,
        limits: ExtractionLimits = ExtractionLimits(),
        name: String = "rootfs"
    ): ExtractionResult {
        File(storageRoot, "arch").mkdirs()
        File(storageRoot, "arch/$name").writeBytes(archive)
        return runBlocking {
            TarGzExtractor(
                fileSystem,
                Dispatchers.IO,
                limits
            ).extract(path("arch/$name"), path("out")) {
            }
        }
    }

    private fun out(name: String) = File(storageRoot, "out/$name")

    private fun error(result: ExtractionResult): ExtractionError =
        (result as ExtractionResult.Failure).error

    // --- xz

    @Test
    fun aTarXzIsUnpackedLikeAGzipOne() {
        val result = unpack(fedoraRootfs().xz())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals("NAME=\"Fedora Linux\"\n", out("usr/lib/os-release").readText())
        assertTrue(out("usr/bin/dnf5").canExecute())
        assertEquals("dnf5", Files.readSymbolicLink(out("usr/bin/dnf").toPath()).toString())
    }

    @Test
    fun theFormatComesFromTheBytesNotFromTheFileName() {
        val gzipNamedXz = unpack(fedoraRootfs().gzip(), name = "fedora.tar.xz")
        val xzNamedGzip = unpack(fedoraRootfs().xz(), name = "fedora.tar.gz")

        assertInstanceOf(ExtractionResult.Success::class.java, gzipNamedXz)
        assertInstanceOf(ExtractionResult.Success::class.java, xzNamedGzip)
    }

    @Test
    fun aTruncatedXzIsCorrupt() {
        val whole = TarBuilder().file("big", "x".repeat(200_000)).xz()

        assertInstanceOf(
            ExtractionError.Corrupt::class.java,
            error(unpack(whole.copyOf(whole.size / 2)))
        )
    }

    @Test
    fun aDamagedXzIsCorrupt() {
        val damaged = TarBuilder().file("big", "x".repeat(200_000)).xz()
        damaged[damaged.size / 2] = (damaged[damaged.size / 2] + 1).toByte()

        assertInstanceOf(ExtractionError.Corrupt::class.java, error(unpack(damaged)))
    }

    @Test
    fun anXzWithOnlyItsMagicIsCorruptNotUnsupported() {
        val magic = byteArrayOf(0xfd.toByte(), 0x37, 0x7a, 0x58, 0x5a, 0x00, 1, 2)

        assertInstanceOf(ExtractionError.Corrupt::class.java, error(unpack(magic)))
    }

    @Test
    fun anXzBombIsRefusedBeforeItFillsTheStorage() {
        // 4 MiB of zeros compress to a few hundred bytes: the limit counts what is written.
        val bomb = TarBuilder().file("zeros", "\u0000".repeat(4 * 1024 * 1024)).xz()

        val result = unpack(bomb, ExtractionLimits(maxTotalBytes = 1024 * 1024))

        assertInstanceOf(ExtractionError.TooLarge::class.java, error(result))
    }

    @Test
    fun anXzThatAsksForMoreMemoryThanAllowedIsRefused() {
        // The dictionary size is the last property of the LZMA2 filter in the block header, which
        // has a CRC that has to be fixed after changing it. The filter is found by its bytes, since
        // the header may or may not carry the sizes of the block.
        val greedy = TarBuilder().file("a", "x").xz()
        val header = XZ_STREAM_HEADER
        val size = ((greedy[header].toInt() and 0xff) + 1) * 4
        val filter = (header + 1 until header + size - 4)
            .first { greedy[it] == LZMA2_FILTER_ID && greedy[it + 1] == LZMA2_PROPERTIES_SIZE }
        greedy[filter + 2] = OVERSIZED_DICTIONARY.toByte()
        val crc = CRC32().apply { update(greedy, header, size - 4) }.value
        for (i in 0 until 4) greedy[header + size - 4 + i] = ((crc shr (8 * i)) and 0xff).toByte()

        assertInstanceOf(ExtractionError.TooLarge::class.java, error(unpack(greedy)))
    }

    // --- OCI image archives

    @Test
    fun aFedoraStyleImageUnpacksItsSingleLayer() {
        val image = ociTar(listOf(layer(fedoraRootfs().gzip()))).xz()

        val result = unpack(image)

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals("NAME=\"Fedora Linux\"\n", out("etc/os-release").readText())
        assertTrue(out("usr/bin/dnf5").canExecute())
        assertEquals("usr/bin", Files.readSymbolicLink(out("bin").toPath()).toString())
        // The wrapper is not part of the root filesystem.
        assertFalse(out("blobs").exists())
        assertFalse(out("index.json").exists())
        assertFalse(out("oci-layout").exists())
    }

    @Test
    fun theWrapperCanBeGzipOrPlainAndItsNamesMayStartWithDotSlash() {
        val layer = layer(fedoraRootfs().gzip())

        assertInstanceOf(ExtractionResult.Success::class.java, unpack(ociTar(listOf(layer)).gzip()))
        assertInstanceOf(ExtractionResult.Success::class.java, unpack(ociTar(listOf(layer)).tar()))
        val dotted = TarBuilder().dir("./blobs").dir("./blobs/sha256")
            .fileBytes("./blobs/sha256/${layer.first}", layer.second).gzip()
        assertInstanceOf(ExtractionResult.Success::class.java, unpack(dotted))
    }

    @Test
    fun aLayerThatIsAPlainTarIsRecognisedByItsHeader() {
        val image = ociTar(listOf(layer(fedoraRootfs().tar()))).xz()

        assertInstanceOf(ExtractionResult.Success::class.java, unpack(image))
        assertEquals("dnf5", Files.readSymbolicLink(out("usr/bin/dnf").toPath()).toString())
    }

    @Test
    fun aLayerThatDoesNotMatchItsNameIsCorrupt() {
        val tampered = "0".repeat(64) to fedoraRootfs().gzip()

        val result = unpack(ociTar(listOf(tampered)).xz())

        assertEquals(
            ExtractionError.Corrupt("the OCI layer does not match its digest"),
            error(result)
        )
    }

    @Test
    fun anImageWithoutALayerIsCorrupt() {
        val result = unpack(ociTar(emptyList()).xz())

        assertEquals(ExtractionError.Corrupt("the OCI archive has no layer"), error(result))
    }

    @Test
    fun anImageOfSeveralLayersIsNotUnpackedByHalves() {
        val first = layer(fedoraRootfs().gzip())
        val second = layer(TarBuilder().file("etc/extra", "x").gzip())

        val result = unpack(ociTar(listOf(first, second)).xz())

        assertEquals(
            ExtractionError.UnsupportedFormat("an OCI image with several layers"),
            error(result)
        )
    }

    @Test
    fun aHostileLayerIsRefusedLikeAnyOtherRootfs() {
        val escaping = layer(TarBuilder().file("../escape", "x").gzip())
        val throughLink = layer(TarBuilder().symlink("link", "/tmp").file("link/pwned", "x").gzip())

        assertInstanceOf(
            ExtractionError.UnsafeEntry::class.java,
            error(unpack(ociTar(listOf(escaping)).xz()))
        )
        assertInstanceOf(
            ExtractionError.UnsafeEntry::class.java,
            error(unpack(ociTar(listOf(throughLink)).xz()))
        )
        assertFalse(File(dir, "escape").exists())
    }

    @Test
    fun aBombInsideALayerIsRefused() {
        val bomb = layer(TarBuilder().file("zeros", "\u0000".repeat(2 * 1024 * 1024)).gzip())

        val result = unpack(ociTar(listOf(bomb)).xz(), ExtractionLimits(maxTotalBytes = 512 * 1024))

        assertInstanceOf(ExtractionError.TooLarge::class.java, error(result))
    }

    @Test
    fun anOciWrapperIsToldApartFromARootfsByItsFirstEntry() {
        assertTrue(OciArchive.isOci("blobs/"))
        assertTrue(OciArchive.isOci("./blobs/"))
        assertTrue(OciArchive.isOci("oci-layout"))
        assertTrue(OciArchive.isOci("index.json"))
        assertTrue(OciArchive.isOci("blobs/sha256/${"a".repeat(64)}"))
        assertFalse(OciArchive.isOci("etc/"))
        assertFalse(OciArchive.isOci("bin"))
        assertFalse(OciArchive.isOci("./"))
    }

    private companion object {
        /** Where the block header starts: after the 12 bytes of the stream header. */
        const val XZ_STREAM_HEADER = 12

        /** The LZMA2 filter in a block header: its id, the size of its properties (one byte), then them. */
        const val LZMA2_FILTER_ID: Byte = 0x21
        const val LZMA2_PROPERTIES_SIZE: Byte = 0x01

        /**
         * The LZMA2 dictionary-size property 36, a dictionary of 1 GiB: valid for the decoder (it
         * accepts up to 37) and far over the 64 MiB allowed, so it is refused for its memory.
         */
        const val OVERSIZED_DICTIONARY = 36
    }
}
