// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.data.storage.NioFileSystemRepository
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import com.qtekfun.ultimateterminal.domain.distro.ExtractionResult
import com.qtekfun.ultimateterminal.domain.model.FsPath
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TarGzExtractorTest {
    @TempDir
    lateinit var dir: File

    private val storageRoot get() = File(dir, "storage").also { it.mkdirs() }
    private val outside get() = File(dir, "outside").also { it.mkdirs() }
    private val fileSystem get() = NioFileSystemRepository(storageRoot.toPath(), Dispatchers.IO) {
        Long.MAX_VALUE
    }

    private fun path(raw: String) = (FsPath.of(raw) as Outcome.Success).value

    private fun extractor(limits: ExtractionLimits = ExtractionLimits()) =
        TarGzExtractor(fileSystem, Dispatchers.IO, limits)

    private fun unpack(
        archive: ByteArray,
        limits: ExtractionLimits = ExtractionLimits(),
        onProgress: (Float?) -> Unit = {}
    ): ExtractionResult {
        File(storageRoot, "arch").mkdirs()
        File(storageRoot, "arch/rootfs").writeBytes(archive)
        return runBlocking {
            extractor(limits).extract(path("arch/rootfs"), path("out"), onProgress)
        }
    }

    private fun out(name: String) = File(storageRoot, "out/$name")

    private fun failure(result: ExtractionResult): ExtractionError =
        (result as ExtractionResult.Failure).error

    private fun unsafe(result: ExtractionResult): ExtractionError.UnsafeEntry =
        failure(result) as ExtractionError.UnsafeEntry

    @Test
    fun aRootFilesystemIsUnpackedWithItsContentModesAndLinks() {
        val archive = TarBuilder()
            .dir("etc")
            .file("etc/hostname", "alpine\n")
            .file("etc/shadow", "secret", mode = 0b110_000_000)
            .dir("bin")
            .file("bin/busybox", "ELF", mode = 0b111_101_101)
            .symlink("bin/sh", "/bin/busybox")
            .symlink("bin/ls", "busybox")
            .gzip()

        val result = unpack(archive)

        val stats = (result as ExtractionResult.Success).stats
        assertEquals(7, stats.entries)
        assertEquals("alpine\n", out("etc/hostname").readText())
        assertTrue(out("bin/busybox").canExecute())
        assertFalse(out("etc/hostname").canExecute())
        val shadow = Files.getPosixFilePermissions(out("etc/shadow").toPath())
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), shadow)
        // Links are kept as they are: an absolute target is only ever resolved inside proot.
        assertEquals("/bin/busybox", Files.readSymbolicLink(out("bin/sh").toPath()).toString())
        assertEquals("busybox", Files.readSymbolicLink(out("bin/ls").toPath()).toString())
        assertEquals(
            TarBuilder.MODIFIED_MILLIS / 1000,
            out("etc/hostname").lastModified() / 1000
        )
    }

    @Test
    fun aPlainTarWithoutCompressionIsAlsoAccepted() {
        val result = unpack(TarBuilder().file("a", "x").tar())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals("x", out("a").readText())
    }

    @Test
    fun leadingDotSlashAndSlashesAreDroppedLikeTarDoes() {
        val archive = TarBuilder()
            .dir("./")
            .file("./etc/os-release", "one")
            .file("/abs/file", "two")
            .gzip()

        val result = unpack(archive)

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals("one", out("etc/os-release").readText())
        assertEquals("two", out("abs/file").readText())
        assertFalse(File("/abs/file").exists())
    }

    @Test
    fun parentDirectoriesWithoutAnEntryAreCreatedForUsableUse() {
        val result = unpack(TarBuilder().file("usr/share/doc/readme", "hi").gzip())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertTrue(out("usr/share/doc").isDirectory)
    }

    @Test
    fun aReadOnlyDirectoryKeepsItsModeButStaysUsableByTheOwner() {
        val archive = TarBuilder()
            .dir("usr", mode = 0b101_101_101)
            .file("usr/bin/tool", "x")
            .gzip()

        val result = unpack(archive)

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        val modes = Files.getPosixFilePermissions(out("usr").toPath())
        // The group and others keep r-x; the owner always has rwx so the app can manage it.
        assertTrue(PosixFilePermission.OWNER_WRITE in modes)
        assertFalse(PosixFilePermission.GROUP_WRITE in modes)
        assertTrue(PosixFilePermission.GROUP_EXECUTE in modes)
    }

    @Test
    fun setuidAndStickyBitsAreDropped() {
        // 0x9ED is octal 04755: the setuid bit plus rwxr-xr-x.
        val result = unpack(TarBuilder().file("su", "x", mode = 0x9ED).gzip())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        val modes = Files.getPosixFilePermissions(out("su").toPath())
        assertEquals(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE
            ),
            modes
        )
    }

    @Test
    fun aLaterEntryReplacesAnEarlierFile() {
        val archive = TarBuilder().file("a", "old").file("a", "new").symlink("a", "b").gzip()

        val result = unpack(archive)

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals("b", Files.readSymbolicLink(out("a").toPath()).toString())
    }

    @Test
    fun devicesAreSkippedAndCounted() {
        val result = unpack(TarBuilder().device("dev/null").file("a", "x").gzip())

        assertEquals(1, (result as ExtractionResult.Success).stats.skippedSpecialFiles)
        assertFalse(out("dev/null").exists())
        assertTrue(out("a").exists())
    }

    @Test
    fun hardLinksShareTheContentOfAnEarlierFile() {
        val archive = TarBuilder().file(
            "bin/busybox",
            "ELF"
        ).hardLink("bin/cat", "bin/busybox").gzip()

        val result = unpack(archive)

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals("ELF", out("bin/cat").readText())
    }

    // --- Hostile archives: nothing may be written outside the destination. ---

    @Test
    fun aParentDirectoryEscapeIsRefused() {
        val result = unpack(TarBuilder().file("../evil", "x").gzip())

        assertEquals("../evil", unsafe(result).name)
        assertFalse(File(storageRoot, "evil").exists())
    }

    @Test
    fun anEscapeHiddenInTheMiddleOfAPathIsRefused() {
        val result = unpack(TarBuilder().file("a/../../evil", "x").gzip())

        assertEquals("contains \"..\"", unsafe(result).reason)
        assertFalse(File(storageRoot, "evil").exists())
    }

    @Test
    fun writingThroughAnAbsoluteSymlinkIsRefused() {
        val target = outside
        val archive = TarBuilder()
            .symlink("link", target.absolutePath)
            .file("link/pwned", "x")
            .gzip()

        val result = unpack(archive)

        assertEquals("passes through a symbolic link", unsafe(result).reason)
        assertEquals(emptyList<String>(), target.list()!!.toList())
    }

    @Test
    fun writingThroughARelativeSymlinkIsRefused() {
        val archive = TarBuilder()
            .symlink("link", "../../outside")
            .file("link/pwned", "x")
            .gzip()

        val result = unpack(archive)

        assertEquals("passes through a symbolic link", unsafe(result).reason)
        assertEquals(emptyList<String>(), outside.list()!!.toList())
    }

    @Test
    fun aLinkInTheMiddleOfAnEntryPathIsRefusedForDirectoriesToo() {
        val target = outside
        val archive = TarBuilder()
            .symlink("escape", target.absolutePath)
            .dir("escape/sub")
            .gzip()

        val result = unpack(archive)

        assertEquals("passes through a symbolic link", unsafe(result).reason)
        assertEquals(emptyList<String>(), target.list()!!.toList())
    }

    @Test
    fun aHardLinkOutsideTheDestinationIsRefused() {
        val secret = File(storageRoot, "secret").also { it.writeText("private") }
        val archive = TarBuilder().hardLink("steal", "../secret").gzip()

        val result = unpack(archive)

        assertEquals("contains \"..\"", unsafe(result).reason)
        assertFalse(out("steal").exists())
        assertEquals("private", secret.readText())
    }

    @Test
    fun aHardLinkToAFileThatIsNotThereIsRefused() {
        val result = unpack(TarBuilder().hardLink("a", "missing").gzip())

        assertEquals("hard link to a file that is not there", unsafe(result).reason)
    }

    @Test
    fun aHardLinkToASymlinkIsRefused() {
        val archive = TarBuilder().symlink("s", "/etc/passwd").hardLink("h", "s").gzip()

        val result = unpack(archive)

        assertEquals("hard link to a file that is not there", unsafe(result).reason)
    }

    @Test
    fun aHardLinkToTheRootItselfIsRefused() {
        val result = unpack(TarBuilder().hardLink("h", ".").gzip())

        assertEquals("hard link to the root", unsafe(result).reason)
    }

    @Test
    fun aFileCannotBeReplacedByADirectoryOrTheOtherWayAround() {
        val fileThenDir = unpack(TarBuilder().file("a", "x").dir("a").gzip())
        assertEquals("replaces a file or link with a directory", unsafe(fileThenDir).reason)

        File(storageRoot, "out").deleteRecursively()
        val dirThenFile = unpack(TarBuilder().dir("a").file("a", "x").gzip())
        assertEquals("replaces a directory", unsafe(dirThenFile).reason)
    }

    @Test
    fun aFileInTheWayOfAParentDirectoryIsRefused() {
        val result = unpack(TarBuilder().file("a", "x").file("a/b", "y").gzip())

        assertEquals("a file is in the way", unsafe(result).reason)
    }

    @Test
    fun aLinkNamedLikeTheRootIsRefused() {
        val result = unpack(TarBuilder().symlink(".", "/").gzip())

        assertInstanceOf(ExtractionError.UnsafeEntry::class.java, failure(result))
    }

    @Test
    fun aSymlinkWithoutATargetIsCorrupt() {
        val result = unpack(TarBuilder().symlink("a", "").gzip())

        assertEquals(
            ExtractionError.Corrupt("symbolic link without a target"),
            failure(result)
        )
    }

    // --- Damaged and unsupported archives. ---

    @Test
    fun otherCompressionFormatsAreReportedByName() {
        assertEquals(
            ExtractionError.UnsupportedFormat("xz"),
            failure(unpack(byteArrayOf(0xfd.toByte(), 0x37, 0x7a, 0x58, 0x5a, 0x00, 1, 2)))
        )
        assertEquals(
            ExtractionError.UnsupportedFormat("bzip2"),
            failure(unpack(byteArrayOf(0x42, 0x5a, 0x68, 0x39, 1, 2)))
        )
        assertEquals(
            ExtractionError.UnsupportedFormat("zstd"),
            failure(unpack(byteArrayOf(0x28, 0xb5.toByte(), 0x2f, 0xfd.toByte(), 1, 2)))
        )
    }

    @Test
    fun aTruncatedGzipIsCorrupt() {
        val whole = TarBuilder().file("big", "x".repeat(100_000)).gzip()

        val result = unpack(whole.copyOf(whole.size / 2))

        assertInstanceOf(ExtractionError.Corrupt::class.java, failure(result))
    }

    @Test
    fun aTarCutInTheMiddleOfAFileIsCorrupt() {
        val whole = TarBuilder().file("big", "x".repeat(5_000)).tar()

        val result = unpack(whole.copyOf(1_000))

        assertInstanceOf(ExtractionError.Corrupt::class.java, failure(result))
    }

    @Test
    fun anEmptyOrGarbageFileIsNotAnArchive() {
        assertEquals(ExtractionError.Corrupt("the archive is empty"), failure(unpack(ByteArray(0))))
        assertEquals(
            ExtractionError.Corrupt("the archive is empty"),
            failure(unpack(TarBuilder().tar()))
        )

        val garbage = unpack(ByteArray(2_000) { (it * 31).toByte() })
        assertInstanceOf(ExtractionResult.Failure::class.java, garbage)
    }

    @Test
    fun errorsAreClassifiedFromTheirMessages() {
        assertEquals(
            ExtractionError.NoSpace,
            IOException("No space left on device").toExtractionError()
        )
        assertEquals(
            ExtractionError.NoSpace,
            IOException("Disk quota exceeded").toExtractionError()
        )
        assertEquals(
            ExtractionError.Corrupt("Corrupted TAR archive."),
            IOException("Corrupted TAR archive.").toExtractionError()
        )
        assertEquals(ExtractionError.Io("boom"), IOException("boom").toExtractionError())
        assertEquals(ExtractionError.Io("IOException"), IOException().toExtractionError())
    }

    // --- Limits, progress and cancellation. ---

    @Test
    fun tooManyEntriesIsRefusedAsABomb() {
        val archive = TarBuilder().file("a", "1").file("b", "2").file("c", "3").gzip()

        val result = unpack(archive, ExtractionLimits(maxEntries = 2))

        assertInstanceOf(ExtractionError.TooLarge::class.java, failure(result))
    }

    @Test
    fun tooManyBytesIsRefusedAsABomb() {
        val archive = TarBuilder().file("a", "x".repeat(10_000)).gzip()

        val result = unpack(archive, ExtractionLimits(maxTotalBytes = 5_000))

        assertInstanceOf(ExtractionError.TooLarge::class.java, failure(result))
    }

    @Test
    fun anAbsurdlyLongPathIsRefused() {
        val archive = TarBuilder().file("a".repeat(300), "x").gzip()

        val result = unpack(archive, ExtractionLimits(maxPathLength = 100))

        assertEquals("invalid name", unsafe(result).reason)
    }

    @Test
    fun progressGoesUpToOneAndNeverBackwards() {
        val archive = TarBuilder().apply {
            repeat(20) { file("f$it", "x".repeat(2_000)) }
        }.gzip()
        val seen = mutableListOf<Float?>()

        unpack(archive) { seen.add(it) }

        assertEquals(20, seen.size)
        assertEquals(seen.filterNotNull().sorted(), seen.filterNotNull())
        assertEquals(1f, seen.last()!!, 0.0001f)
    }

    @Test
    fun cancellingStopsTheExtractionAndRethrows() {
        val archive = TarBuilder().apply { repeat(50) { file("f$it", "x") } }.gzip()
        File(storageRoot, "arch").mkdirs()
        File(storageRoot, "arch/rootfs").writeBytes(archive)
        val holder = AtomicReference<Job>()
        var completed: ExtractionResult? = null

        runBlocking {
            val job = CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.LAZY) {
                completed = extractor().extract(path("arch/rootfs"), path("out")) {
                    holder.get()?.cancel()
                }
            }
            holder.set(job)
            job.start()
            job.join()
            assertTrue(job.isCancelled)
        }

        assertEquals(null, completed)
        // It stopped early: not every file was written.
        assertTrue((File(storageRoot, "out").list()?.size ?: 0) < 50)
    }

    @Test
    fun aGlobalPaxHeaderIsIgnored() {
        val result = unpack(TarBuilder().globalPaxHeader().file("a", "x").gzip())

        assertEquals(ExtractionResult.Success::class.java, result::class.java, result.toString())
        assertEquals("x", out("a").readText())
        assertFalse(out("pax_global_header").exists())
    }

    @Test
    fun aNulByteInANameCannotSmuggleInAPath() {
        // Commons Compress cuts a name at the first NUL, like tar does: what is validated is what
        // is written, so "ok" + NUL + "../../evil" can only ever create "ok".
        val result = unpack(TarBuilder().file("ok\u0000/../../evil", "x").gzip())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertTrue(out("ok").exists())
        assertFalse(File(storageRoot, "evil").exists())
        assertFalse(File(dir, "evil").exists())
    }

    @Test
    fun aDirectoryCannotReplaceASymlink() {
        val result = unpack(TarBuilder().symlink("a", "/tmp").dir("a").gzip())

        assertEquals("replaces a file or link with a directory", unsafe(result).reason)
    }

    @Test
    fun theSameDirectoryTwiceIsFine() {
        val result = unpack(TarBuilder().dir("a").dir("a").file("a/f", "x").gzip())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals("x", out("a/f").readText())
    }

    @Test
    fun aRootModeEntryIsAppliedToTheDestination() {
        val result = unpack(TarBuilder().dir(".", mode = 0b111_101_000).file("a", "x").gzip())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        val modes = Files.getPosixFilePermissions(File(storageRoot, "out").toPath())
        assertTrue(PosixFilePermission.GROUP_READ in modes)
        assertFalse(PosixFilePermission.OTHERS_READ in modes)
    }

    @Test
    fun aBigFileIsCopiedWholeAndCheckedForCancellationAlongTheWay() {
        val content = "0123456789abcdef".repeat(300_000)
        val result = unpack(TarBuilder().file("big", content).gzip())

        assertInstanceOf(ExtractionResult.Success::class.java, result)
        assertEquals(content.length.toLong(), out("big").length())
    }

    @Test
    fun truncatedHeadersAreReportedAsCorruptWhateverTheWording() {
        assertEquals(
            ExtractionError.Corrupt("Unexpected EOF in header"),
            IOException("Unexpected EOF in header").toExtractionError()
        )
        assertEquals(
            ExtractionError.Corrupt("Truncated TAR archive"),
            IOException("Truncated TAR archive").toExtractionError()
        )
    }

    @Test
    fun theCountingStreamCountsSingleBytesAndBlocks() {
        val counting = CountingInputStream(byteArrayOf(1, 2, 3, 4, 5).inputStream())

        assertEquals(1, counting.read())
        assertEquals(1L, counting.count)
        assertEquals(4, counting.read(ByteArray(10), 0, 10))
        assertEquals(5L, counting.count)
        assertEquals(-1, counting.read())
        assertEquals(-1, counting.read(ByteArray(2), 0, 2))
        assertEquals(5L, counting.count)
        assertEquals(0, counting.available())
        counting.close()
    }

    @Test
    fun theFormatIsDetectedEvenWhenTheStreamHandsOutOneByteAtATime() {
        // A single read() may return fewer bytes than asked; the check must keep reading.
        val gzip = TarBuilder().file("a", "x").gzip()
        val trickle = object : java.io.FilterInputStream(gzip.inputStream()) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
        }

        val opened = ArchiveFormat.open(java.io.BufferedInputStream(trickle, 1))

        assertInstanceOf(ArchiveFormat.Result.Opened::class.java, opened)
    }

    @Test
    fun aStreamShorterThanTheLookaheadIsStillClassified() {
        val opened = ArchiveFormat.open(
            java.io.BufferedInputStream(byteArrayOf(0x1f).inputStream())
        )

        // One byte is not a full gzip magic: it falls through to plain tar, which the tar reader
        // then rejects as corrupt.
        assertInstanceOf(ArchiveFormat.Result.Opened::class.java, opened)
    }
}
