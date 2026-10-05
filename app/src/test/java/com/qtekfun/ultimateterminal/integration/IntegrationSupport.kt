// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.integration

import com.qtekfun.ultimateterminal.data.backup.Device
import com.qtekfun.ultimateterminal.data.rootfs.HttpRootfsDownloader
import com.qtekfun.ultimateterminal.data.rootfs.TarBuilder
import com.qtekfun.ultimateterminal.domain.distro.DistroInstaller
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import com.qtekfun.ultimateterminal.fakes.FakeCatalog
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/**
 * A small Alpine-shaped root filesystem as a `.tar.gz`: the pieces a session needs (busybox, a
 * `/bin/sh` link, passwd and group, a home) and a few files whose content a test can look for.
 */
internal fun alpineTarGz(marker: String = "one"): ByteArray = TarBuilder()
    .dir("etc")
    .file("etc/os-release", "NAME=\"Alpine Linux\"\nMARKER=$marker\n")
    .file("etc/passwd", "root:x:0:0:root:/root:/bin/sh\n")
    .file("etc/group", "root:x:0:root\n")
    .file("etc/shells", "/bin/sh\n")
    .dir("bin")
    .file("bin/busybox", "ELF-$marker", mode = 0b111_101_101)
    .symlink("bin/sh", "/bin/busybox")
    .dir("root")
    .file("root/notes.txt", "hello $marker\n")
    .gzip()

internal fun sha256Of(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * The real download, verification, extraction and registration pipeline over one [Device]; only
 * the catalog is a stand-in, because the official indexes are the one thing a host test cannot
 * reach (T06 tests the parsers on their own).
 */
internal fun Device.installerFor(
    url: String,
    archive: ByteArray,
    sha256: String = sha256Of(archive)
) = DistroInstaller(
    catalog = FakeCatalog(
        RootfsResult.Success(
            RootfsSource(
                DistroFamily.ALPINE,
                Architecture.ARM64,
                "3.22.1",
                url,
                sha256,
                archive.size.toLong()
            )
        )
    ),
    downloader = HttpRootfsDownloader(
        client = OkHttpClient(),
        io = Dispatchers.IO,
        maxAttempts = 1,
        requireHttps = false
    ),
    extractor = extractor,
    fileSystem = fileSystem,
    distros = distros,
    architecture = { Architecture.ARM64 }
)

/** What a directory holds, keyed by relative path: file contents and symbolic link targets. */
internal fun treeOf(root: File): Map<String, String> {
    val base = root.toPath()
    return Files.walk(base).use { paths ->
        paths.filter { it != base }.toList().associate { path ->
            val key = base.relativize(path).toString()
            key to when {
                Files.isSymbolicLink(path) -> "-> " + Files.readSymbolicLink(path)
                Files.isDirectory(path) -> "dir"
                else -> "file " + String(Files.readAllBytes(path))
            }
        }
    }
}
