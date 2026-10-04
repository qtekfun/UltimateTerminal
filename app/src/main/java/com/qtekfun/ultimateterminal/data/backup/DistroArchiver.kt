// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupPhase
import com.qtekfun.ultimateterminal.domain.backup.BackupProgress
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A distro archived into the work directory, ready to be copied into the backup. */
internal class ArchivedDistro(val file: Path, val part: ManifestPart)

/** Archives distros into files of the work directory, hashing them as they are written. */
internal class DistroArchiver(private val fileSystem: FileSystemRepository) {
    private val archiver = RootfsArchiver()

    /** Part [index] of [total]; [onProgress] gets the fraction of the whole job that is done. */
    suspend fun archive(
        distro: Distro,
        work: FsPath,
        index: Int,
        total: Int,
        onProgress: (BackupProgress) -> Unit
    ): ArchivedDistro {
        val name = PartNames.distro(index)
        val target = Paths.get(fileSystem.absolutePathOf(work)).resolve("distro-$index.tar.gz")
        val source = Paths.get(fileSystem.absolutePathOf(distro.directory))
        val expected = maxOf(distro.sizeBytes, 1L)
        val context = currentCoroutineContext()
        val output = DigestingOutputStream(Files.newOutputStream(target))
        archiver.write(source, output) { bytes ->
            context.ensureActive()
            val whole = (bytes.toDouble() / expected).coerceIn(0.0, 1.0)
            onProgress(BackupProgress(BackupPhase.ARCHIVING, ((index + whole) / total).toFloat()))
        }
        val meta = DistroMeta(
            distro.name,
            distro.type.name,
            distro.release,
            distro.defaultUser,
            distro.isDefault
        )
        return ArchivedDistro(target, ManifestPart(name, output.sha256(), output.bytes, meta))
    }
}
