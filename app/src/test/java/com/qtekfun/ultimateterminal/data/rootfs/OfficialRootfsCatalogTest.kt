// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OfficialRootfsCatalogTest {
    private val server = MockWebServer()
    private val paths = mutableMapOf<String, String>()
    private val requested = mutableListOf<String>()

    @BeforeEach
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.target
                requested += path
                val body = paths[path]
                return if (body == null) {
                    MockResponse.Builder().code(404).build()
                } else {
                    MockResponse.Builder().code(200).body(body).build()
                }
            }
        }
        server.start()
    }

    @AfterEach
    fun stop() = runCatching { server.close() }.let { }

    private fun base(path: String) = server.url(path).toString().trimEnd('/')

    private fun catalog() = OfficialRootfsCatalog(
        client = OkHttpClient(),
        endpoints = RootfsEndpoints(
            alpineReleases = base("/alpine/releases"),
            ubuntuBase = base("/ubuntu/releases"),
            ubuntuRelease = "24.04",
            debianArtifacts = base("/debian"),
            debianSuite = "trixie"
        ),
        io = Dispatchers.Unconfined
    )

    @Test
    fun alpineIsResolvedFromThePerArchitectureIndex() = runTest {
        paths["/alpine/releases/aarch64/latest-releases.yaml"] = Samples.ALPINE_YAML

        val result = catalog().resolve(DistroFamily.ALPINE, Architecture.ARM64)

        val source = (result as RootfsResult.Success).value
        assertEquals(Samples.ALPINE_SHA, source.sha256)
        assertEquals(
            base("/alpine/releases/aarch64/alpine-minirootfs-3.24.2-aarch64.tar.gz"),
            source.url
        )
        assertEquals(4_028_030L, source.sizeBytes)
    }

    @Test
    fun alpineMapsEachArchitectureToItsAlpineName() = runTest {
        for ((arch, name) in mapOf(
            Architecture.ARM64 to "aarch64",
            Architecture.ARMV7 to "armv7",
            Architecture.X86_64 to "x86_64"
        )) {
            paths["/alpine/releases/$name/latest-releases.yaml"] = Samples.ALPINE_YAML
            assertTrue(catalog().resolve(DistroFamily.ALPINE, arch) is RootfsResult.Success, name)
        }
    }

    @Test
    fun ubuntuIsResolvedFromSha256SumsOfTheRelease() = runTest {
        paths["/ubuntu/releases/24.04/release/SHA256SUMS"] = Samples.UBUNTU_SUMS

        val source = (
            catalog().resolve(
                DistroFamily.UBUNTU,
                Architecture.ARM64
            ) as RootfsResult.Success
            ).value

        assertEquals("24.04.10", source.version)
        assertEquals(
            base("/ubuntu/releases/24.04/release/ubuntu-base-24.04.10-base-arm64.tar.gz"),
            source.url
        )
    }

    @Test
    fun ubuntuMapsEachArchitectureToItsDebianStyleName() = runTest {
        paths["/ubuntu/releases/24.04/release/SHA256SUMS"] = Samples.UBUNTU_SUMS

        assertTrue(
            catalog().resolve(DistroFamily.UBUNTU, Architecture.ARMV7) is RootfsResult.Success
        )
        assertTrue(
            catalog().resolve(DistroFamily.UBUNTU, Architecture.X86_64) is RootfsResult.Success
        )
    }

    @Test
    fun debianIsResolvedFromTheOciManifestOfTheRightBranch() = runTest {
        paths["/debian/dist-arm64v8/trixie/slim/oci/blobs/image-manifest.json"] =
            Samples.DEBIAN_MANIFEST

        val source = (
            catalog().resolve(
                DistroFamily.DEBIAN,
                Architecture.ARM64
            ) as RootfsResult.Success
            ).value

        assertEquals(Samples.DEBIAN_SHA, source.sha256)
        assertEquals(base("/debian/dist-arm64v8/trixie/slim/oci/blobs/rootfs.tar.gz"), source.url)
        assertEquals(30_189_691L, source.sizeBytes)
    }

    @Test
    fun debianMapsEachArchitectureToItsDockerBranch() = runTest {
        for (branch in listOf("dist-arm32v7", "dist-amd64")) {
            paths["/debian/$branch/trixie/slim/oci/blobs/image-manifest.json"] =
                Samples.DEBIAN_MANIFEST
        }

        assertTrue(
            catalog().resolve(DistroFamily.DEBIAN, Architecture.ARMV7) is RootfsResult.Success
        )
        assertTrue(
            catalog().resolve(DistroFamily.DEBIAN, Architecture.X86_64) is RootfsResult.Success
        )
    }

    @Test
    fun aMissingIndexIsReportedAsUnavailableWithItsStatus() = runTest {
        val result = catalog().resolve(DistroFamily.UBUNTU, Architecture.ARM64)

        val error = (result as RootfsResult.Failure).error as RootfsError.CatalogUnavailable
        assertTrue("404" in error.reason, error.reason)
    }

    @Test
    fun anIndexWithoutAUsableEntryIsReportedAsUnavailable() = runTest {
        paths["/alpine/releases/aarch64/latest-releases.yaml"] = "---\n"
        paths["/ubuntu/releases/24.04/release/SHA256SUMS"] = "garbage"
        paths["/debian/dist-arm64v8/trixie/slim/oci/blobs/image-manifest.json"] = "{}"

        for (family in DistroFamily.entries) {
            val result = catalog().resolve(family, Architecture.ARM64)
            assertTrue(
                result is RootfsResult.Failure && result.error is RootfsError.CatalogUnavailable,
                "$family"
            )
        }
    }

    @Test
    fun aServerThatIsDownIsReportedAsUnavailable() = runTest {
        val catalog = catalog()
        server.close()

        val result = catalog.resolve(DistroFamily.ALPINE, Architecture.ARM64)

        assertTrue(result is RootfsResult.Failure && result.error is RootfsError.CatalogUnavailable)
    }
}
