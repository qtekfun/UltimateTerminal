// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.distro.ExtractionError
import com.qtekfun.ultimateterminal.domain.distro.ExtractionResult
import com.qtekfun.ultimateterminal.domain.distro.ExtractionStats
import com.qtekfun.ultimateterminal.domain.distro.RootfsExtractor
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.io.BufferedInputStream
import java.io.EOFException
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
 * Unpacks a `.tar.gz` (or plain `.tar`) root filesystem with Apache Commons Compress.
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
) : RootfsExtractor {

    override suspend fun extract(
        archive: FsPath,
        destination: FsPath,
        onProgress: (Float?) -> Unit
    ): ExtractionResult = withContext(io) {
        val archivePath = Paths.get(fileSystem.absolutePathOf(archive))
        val root = Paths.get(fileSystem.absolutePathOf(destination))
        try {
            ExtractionResult.Success(unpack(this, archivePath, root, onProgress))
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
        archivePath: Path,
        root: Path,
        onProgress: (Float?) -> Unit
    ): ExtractionStats {
        val total = Files.size(archivePath)
        CountingInputStream(Files.newInputStream(archivePath)).use { counting ->
            val tarStream = when (val opened = ArchiveFormat.open(BufferedInputStream(counting))) {
                is ArchiveFormat.Result.Unsupported -> throw ExtractionFailure(opened.error)
                is ArchiveFormat.Result.Opened -> opened.stream
            }
            TarArchiveInputStream(tarStream).use { tar ->
                val pass = TarPass(scope, SafeTreeWriter(root, limits.maxPathLength), limits, tar)
                return pass.run {
                    onProgress((counting.count.toDouble() / total).toFloat().coerceIn(0f, 1f))
                }
            }
        }
    }
}

internal fun IOException.toExtractionError(): ExtractionError {
    val text = message.orEmpty()
    return when {
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
    private val tar: TarArchiveInputStream
) {
    private var entries = 0L
    private var bytes = 0L
    private var skipped = 0L

    fun run(reportProgress: () -> Unit): ExtractionStats {
        writer.begin()
        while (true) {
            scope.ensureActive()
            val entry: TarArchiveEntry = tar.nextEntry ?: break
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
