// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.backup.StreamRootfsExtractor
import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import com.qtekfun.ultimateterminal.domain.distro.ExtractionResult
import com.qtekfun.ultimateterminal.domain.distro.ExtractionStats
import com.qtekfun.ultimateterminal.domain.distro.RootfsExtractor
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.MemoryLimitException
import org.tukaani.xz.XZIOException

/** Ceilings far above any real root filesystem, so only a decompression bomb reaches them. */
data class ExtractionLimits(
    val maxEntries: Long = DEFAULT_MAX_ENTRIES,
    val maxTotalBytes: Long = DEFAULT_MAX_BYTES,
    val maxPathLength: Int = DEFAULT_MAX_PATH
) {
    private companion object {
        const val DEFAULT_MAX_ENTRIES = 2_000_000L
        const val DEFAULT_MAX_BYTES = 16L * 1024 * 1024 * 1024
        const val DEFAULT_MAX_PATH = 4096
    }
}

/**
 * Unpacks a root filesystem with Apache Commons Compress: a `.tar.gz`, a `.tar.xz`, a plain `.tar`,
 * or an OCI image archive (Fedora's `.oci.tar.xz`) whose single layer is the rootfs. The format is
 * read from the first bytes, never from the name.
 *
 * Security (see `DECISIONS.md`, T07): an entry is written only inside the destination, and never
 * through a symbolic link, because the next entries of a rootfs could otherwise be steered
 * outside it ([SafeTreeWriter] holds those rules). Symbolic links themselves are stored as they
 * are: absolute ones such as `/bin/sh -> /bin/busybox` are normal in a rootfs and are only ever
 * resolved inside proot. Ownership is not restored (every file belongs to the app) and the
 * setuid, setgid and sticky bits are dropped. Devices, sockets and pipes are skipped, since they
 * cannot be created without root.
 */
class TarGzExtractor(
    private val fileSystem: FileSystemRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val limits: ExtractionLimits = ExtractionLimits()
) : RootfsExtractor,
    StreamRootfsExtractor {
    override suspend fun extract(
        archive: FsPath,
        destination: FsPath,
        onProgress: (Float?) -> Unit
    ): ExtractionResult {
        val archivePath = Paths.get(fileSystem.absolutePathOf(archive))
        val root = Paths.get(fileSystem.absolutePathOf(destination))
        return guarded { scope ->
            Files.newInputStream(archivePath).use { input ->
                unpack(scope, input, Files.size(archivePath), root, onProgress)
            }
        }
    }

    override suspend fun extract(
        input: InputStream,
        totalBytes: Long,
        destination: FsPath,
        onProgress: (Float?) -> Unit
    ): ExtractionResult {
        val root = Paths.get(fileSystem.absolutePathOf(destination))
        // The caller owns the stream (it is a part of a bigger file), so it is not closed here.
        val shared = NonClosingInputStream(input)
        return guarded { scope -> unpack(scope, shared, totalBytes, root, onProgress) }
    }

    private suspend fun guarded(work: (CoroutineScope) -> ExtractionStats): ExtractionResult =
        withContext(io) {
            try {
                ExtractionResult.Success(work(this))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractionFailure) {
                ExtractionResult.Failure(e.error)
            } catch (e: EOFException) {
                ExtractionResult.Failure(ExtractionError.Corrupt(e.message ?: "unexpected end"))
            } catch (e: ZipException) {
                ExtractionResult.Failure(ExtractionError.Corrupt(e.message ?: "bad gzip data"))
            } catch (e: IOException) {
                ExtractionResult.Failure(e.toExtractionError())
            }
        }

    private fun unpack(
        scope: CoroutineScope,
        input: InputStream,
        totalBytes: Long,
        root: Path,
        onProgress: (Float?) -> Unit
    ): ExtractionStats {
        val total = maxOf(totalBytes, 1L)
        CountingInputStream(input).use { counting ->
            val progress = {
                onProgress((counting.count.toDouble() / total).toFloat().coerceIn(0f, 1f))
            }
            TarArchiveInputStream(openTar(counting)).use { outer ->
                val first = outer.nextEntry
                val writer = SafeTreeWriter(root, limits.maxPathLength)
                return if (first != null && OciArchive.isOci(first.name)) {
                    unpackOciLayer(scope, outer, first, writer, progress)
                } else {
                    TarPass(scope, writer, limits, outer, first).run(progress)
                }
            }
        }
    }

    /** The one layer of an OCI image, checked against the digest its blob is named after. */
    private fun unpackOciLayer(
        scope: CoroutineScope,
        outer: TarArchiveInputStream,
        first: TarArchiveEntry,
        writer: SafeTreeWriter,
        progress: () -> Unit
    ): ExtractionStats {
        val layer = OciArchive.findLayer(outer, first)
        val stats = TarArchiveInputStream(openTar(layer.stream)).use { inner ->
            TarPass(scope, writer, limits, inner, null).run(progress)
        }
        layer.verify()
        OciArchive.requireNoOtherLayer(outer)
        return stats
    }

    private fun openTar(source: InputStream): InputStream =
        when (val opened = ArchiveFormat.open(BufferedInputStream(source))) {
            is ArchiveFormat.Result.Unsupported -> throw ExtractionFailure(opened.error)
            is ArchiveFormat.Result.Opened -> opened.stream
        }
}

/** Lets a part of a bigger stream be read without the reader closing the whole thing. */
private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
}

internal fun IOException.toExtractionError(): ExtractionError {
    val text = message.orEmpty()
    return when {
        // The xz decoder's own errors: a stream that asks for more memory than allowed is a bomb or
        // not an archive for this device; anything else it rejects is damage.
        this is MemoryLimitException -> ExtractionError.TooLarge(
            "xz needs more memory than allowed"
        )

        this is XZIOException -> ExtractionError.Corrupt(text.ifEmpty { "bad xz data" })

        text.contains("No space left", ignoreCase = true) ||
            text.contains("quota exceeded", ignoreCase = true) -> ExtractionError.NoSpace

        // Commons Compress reports damaged tar headers as plain IOExceptions.
        text.contains("Corrupted TAR", ignoreCase = true) ||
            text.contains("Unexpected EOF", ignoreCase = true) ||
            text.contains("Truncated TAR", ignoreCase = true) ->
            ExtractionError.Corrupt(text)

        else -> ExtractionError.Io(text.ifEmpty { javaClass.simpleName })
    }
}

/** Counts the bytes read from the file, to report progress against the archive's size. */
internal class CountingInputStream(private val delegate: InputStream) : InputStream() {
    var count: Long = 0
        private set

    override fun read(): Int = delegate.read().also { if (it >= 0) count++ }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length).also { if (it > 0) count += it }

    override fun available(): Int = delegate.available()

    override fun close() = delegate.close()
}

/** One pass over a tar stream: reads the entries, enforces the limits, hands each to the writer. */
private class TarPass(
    private val scope: CoroutineScope,
    private val writer: SafeTreeWriter,
    private val limits: ExtractionLimits,
    private val tar: TarArchiveInputStream,
    firstEntry: TarArchiveEntry?
) {
    /** The caller reads the first entry to tell a rootfs from an OCI archive, so it is handed over. */
    private var pending: TarArchiveEntry? = firstEntry
    private var entries = 0L
    private var bytes = 0L
    private var skipped = 0L

    fun run(reportProgress: () -> Unit): ExtractionStats {
        writer.begin()
        while (true) {
            scope.ensureActive()
            val entry: TarArchiveEntry = next() ?: break
            entries++
            if (entries > limits.maxEntries) {
                throw ExtractionFailure(
                    ExtractionError.TooLarge("more than ${limits.maxEntries} entries")
                )
            }
            handle(entry)
            reportProgress()
        }
        // A root filesystem without a single entry is a damaged or wrong file, never a distro.
        if (entries == 0L) throw ExtractionFailure(ExtractionError.Corrupt("the archive is empty"))
        writer.finish()
        return ExtractionStats(entries, bytes, skipped)
    }

    private fun next(): TarArchiveEntry? = pending?.also { pending = null } ?: tar.nextEntry

    private fun handle(entry: TarArchiveEntry) {
        if (entry.isGlobalPaxHeader) return
        if (entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO) {
            skipped++
            return
        }
        // Only a directory entry may name the destination itself (the usual "./").
        val target = writer.resolve(entry.name)
        when {
            entry.isDirectory -> writer.directory(entry.name, target, entry.mode)
            entry.isSymbolicLink -> writer.symlink(entry.name, target, entry.linkName)
            entry.isLink -> writer.hardLink(entry.name, target, entry.linkName)
            entry.isFile -> writeFile(entry, target)
            else -> skipped++
        }
    }

    private fun writeFile(entry: TarArchiveEntry, target: Path?) {
        var sinceCheck = 0
        writer.file(entry.name, target, entry.mode, tar) { read ->
            bytes += read
            if (bytes > limits.maxTotalBytes) {
                throw ExtractionFailure(
                    ExtractionError.TooLarge("more than ${limits.maxTotalBytes} bytes")
                )
            }
            sinceCheck += read
            if (sinceCheck >= CHECK_EVERY_BYTES) {
                sinceCheck = 0
                scope.ensureActive()
            }
        }
        target?.let { path ->
            runCatching { Files.setLastModifiedTime(path, entry.lastModifiedTime) }
        }
    }

    private companion object {
        const val CHECK_EVERY_BYTES = 1024 * 1024
    }
}
