// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsCatalog
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where each distribution's official artifacts and indexes live. Overridden in tests. */
data class RootfsEndpoints(
    val alpineReleases: String = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases",
    val ubuntuBase: String = "https://cdimage.ubuntu.com/ubuntu-base/releases",
    val ubuntuRelease: String = "24.04",
    val debianArtifacts: String =
        "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts",
    val debianSuite: String = "trixie",
    val fedoraReleases: String = "https://dl.fedoraproject.org/pub/fedora/linux/releases"
)

/**
 * Resolves the current archive from each distribution's own official index, so no URL or hash is
 * hard-coded and a new point release is picked up without an app update. See `DECISIONS.md` (T06).
 */
class OfficialRootfsCatalog(
    private val client: OkHttpClient,
    private val endpoints: RootfsEndpoints = RootfsEndpoints(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val userAgent: String = "UltimateTerminal"
) : RootfsCatalog {

    override suspend fun resolve(
        family: DistroFamily,
        architecture: Architecture
    ): RootfsResult<RootfsSource> = when (family) {
        DistroFamily.ALPINE -> alpine(architecture)
        DistroFamily.UBUNTU -> ubuntu(architecture)
        DistroFamily.DEBIAN -> debian(architecture)
        DistroFamily.FEDORA -> fedora(architecture)
    }

    private suspend fun alpine(architecture: Architecture): RootfsResult<RootfsSource> {
        val arch = when (architecture) {
            Architecture.ARM64 -> "aarch64"
            Architecture.ARMV7 -> "armv7"
            Architecture.X86_64 -> "x86_64"
        }
        val base = "${endpoints.alpineReleases}/$arch"
        return fetch("$base/latest-releases.yaml").andThen {
            IndexParsers.alpine(
                it,
                architecture,
                base
            ).orUnavailable("Alpine index has no minirootfs for $arch")
        }
    }

    private suspend fun ubuntu(architecture: Architecture): RootfsResult<RootfsSource> {
        val arch = when (architecture) {
            Architecture.ARM64 -> "arm64"
            Architecture.ARMV7 -> "armhf"
            Architecture.X86_64 -> "amd64"
        }
        val base = "${endpoints.ubuntuBase}/${endpoints.ubuntuRelease}/release"
        return fetch("$base/SHA256SUMS").andThen {
            IndexParsers.ubuntu(
                it,
                architecture,
                arch,
                base
            ).orUnavailable("Ubuntu SHA256SUMS has no base for $arch")
        }
    }

    private suspend fun debian(architecture: Architecture): RootfsResult<RootfsSource> {
        val branch = when (architecture) {
            Architecture.ARM64 -> "dist-arm64v8"
            Architecture.ARMV7 -> "dist-arm32v7"
            Architecture.X86_64 -> "dist-amd64"
        }
        val blobs = "${endpoints.debianArtifacts}/$branch/${endpoints.debianSuite}/slim/oci/blobs"
        return fetch("$blobs/image-manifest.json").andThen {
            IndexParsers.debian(it, architecture, endpoints.debianSuite, "$blobs/rootfs.tar.gz")
                .orUnavailable("Debian manifest has no usable layer for $branch")
        }
    }

    /**
     * Fedora's base container image, from the signed `CHECKSUM` of the newest release that has one.
     * It is an OCI archive compressed with xz, and Fedora publishes no 32-bit ARM image.
     */
    private suspend fun fedora(architecture: Architecture): RootfsResult<RootfsSource> {
        val arch = when (architecture) {
            Architecture.ARM64 -> "aarch64"
            Architecture.X86_64 -> "x86_64"
            Architecture.ARMV7 -> null
        }
        return if (arch == null) {
            RootfsResult.Failure(RootfsError.CatalogUnavailable("Fedora has no image for armv7"))
        } else {
            fetch("${endpoints.fedoraReleases}/").andThen { listing ->
                fedoraNewest(IndexParsers.fedoraVersions(listing), architecture, arch)
            }
        }
    }

    /** The newest release that has an image: the very latest can be a branch still in the works. */
    private suspend fun fedoraNewest(
        versions: List<Int>,
        architecture: Architecture,
        arch: String
    ): RootfsResult<RootfsSource> {
        var result: RootfsResult<RootfsSource> =
            RootfsResult.Failure(RootfsError.CatalogUnavailable("no Fedora release for $arch"))
        val candidates = versions.take(FEDORA_RELEASES_TRIED).iterator()
        while (result is RootfsResult.Failure && candidates.hasNext()) {
            result = fedoraRelease(candidates.next(), architecture, arch)
        }
        return result
    }

    private suspend fun fedoraRelease(
        version: Int,
        architecture: Architecture,
        arch: String
    ): RootfsResult<RootfsSource> {
        val images = "${endpoints.fedoraReleases}/$version/Container/$arch/images"
        return fetch("$images/").andThen { listing ->
            val image = IndexParsers.fedoraImage(listing, version, arch)
            if (image == null) {
                RootfsResult.Failure(
                    RootfsError.CatalogUnavailable("Fedora $version has no image for $arch")
                )
            } else {
                fetch("$images/${image.checksumFile}").andThen { text ->
                    IndexParsers.fedoraChecksum(text, image.file)?.let {
                        RootfsResult.Success(
                            RootfsSource(
                                DistroFamily.FEDORA,
                                architecture,
                                version.toString(),
                                "$images/${image.file}",
                                it.sha256,
                                it.sizeBytes
                            )
                        )
                    } ?: RootfsResult.Failure(
                        RootfsError.CatalogUnavailable(
                            "Fedora CHECKSUM has no entry for ${image.file}"
                        )
                    )
                }
            }
        }
    }

    private suspend fun fetch(url: String): RootfsResult<String> = withContext(io) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    RootfsResult.Success(response.body.string())
                } else {
                    RootfsResult.Failure(
                        RootfsError.CatalogUnavailable("HTTP ${response.code} from $url")
                    )
                }
            }
        } catch (e: IOException) {
            RootfsResult.Failure(
                RootfsError.CatalogUnavailable(e.message ?: e.javaClass.simpleName)
            )
        }
    }

    private inline fun <A, B> RootfsResult<A>.andThen(
        next: (A) -> RootfsResult<B>
    ): RootfsResult<B> = when (this) {
        is RootfsResult.Success -> next(value)
        is RootfsResult.Failure -> this
    }

    private fun RootfsSource?.orUnavailable(reason: String): RootfsResult<RootfsSource> =
        this?.let { RootfsResult.Success(it) }
            ?: RootfsResult.Failure(RootfsError.CatalogUnavailable(reason))

    private companion object {
        /** How many of the newest releases are tried before giving up. */
        const val FEDORA_RELEASES_TRIED = 3
    }
}
