// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parsers for the three official indexes. They are pure functions over the text of the index, so they
 * are tested with real samples and do not touch the network. Each returns null when the index does not
 * contain a usable entry.
 */
internal object IndexParsers {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Alpine's `latest-releases.yaml`: a list of entries whose fields sit at a two-space indent. The
     * `alpine-minirootfs` entry carries the file name, version, size and SHA-256. [baseUrl] is the
     * directory the file is served from.
     */
    fun alpine(yaml: String, architecture: Architecture, baseUrl: String): RootfsSource? {
        val entries = mutableListOf<MutableMap<String, String>>()
        for (line in yaml.lineSequence()) {
            if (line.startsWith("-")) entries += mutableMapOf()
            val match = ALPINE_FIELD.matchEntire(line) ?: continue
            entries.lastOrNull()?.put(match.groupValues[1], match.groupValues[2].trim())
        }
        val entry = entries.firstOrNull { it["flavor"] == "alpine-minirootfs" }
            ?.takeIf { fields -> ALPINE_KEYS.all { it in fields } }
        return entry?.let { fields ->
            fields.getValue("size").toLongOrNull()?.let { size ->
                val file = fields.getValue("file")
                RootfsSource(
                    DistroFamily.ALPINE,
                    architecture,
                    fields.getValue("version"),
                    "$baseUrl/$file",
                    fields.getValue("sha256"),
                    size
                )
            }
        }
    }

    /**
     * Ubuntu Base's `SHA256SUMS`: lines of `<sha256> *ubuntu-base-<version>-base-<arch>.tar.gz`. Several
     * point releases are listed; the highest version for [ubuntuArch] wins. The index has no sizes.
     */
    fun ubuntu(
        sums: String,
        architecture: Architecture,
        ubuntuArch: String,
        baseUrl: String
    ): RootfsSource? {
        val best = sums.lineSequence()
            .mapNotNull { UBUNTU_LINE.matchEntire(it.trim())?.destructured }
            .filter { (_, _, arch) -> arch == ubuntuArch }
            .maxWithOrNull { (_, a, _), (_, b, _) -> compareVersions(a, b) }
        return best?.let { (sha256, version, arch) ->
            val file = "ubuntu-base-$version-base-$arch.tar.gz"
            RootfsSource(DistroFamily.UBUNTU, architecture, version, "$baseUrl/$file", sha256, null)
        }
    }

    /**
     * Debian's official rootfs is the single gzip layer of the OCI image published in
     * `debuerreotype/docker-debian-artifacts`; its manifest gives the layer's digest and size.
     */
    fun debian(
        manifestJson: String,
        architecture: Architecture,
        suite: String,
        layerUrl: String
    ): RootfsSource? {
        val layer = runCatching { json.decodeFromString<OciManifest>(manifestJson) }
            .getOrNull()?.layers?.singleOrNull()
        val sha256 = layer?.digest?.takeIf {
            it.startsWith(SHA256_PREFIX)
        }?.removePrefix(SHA256_PREFIX)
        return if (layer != null && sha256 != null) {
            RootfsSource(DistroFamily.DEBIAN, architecture, suite, layerUrl, sha256, layer.size)
        } else {
            null
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private val ALPINE_KEYS = listOf("file", "version", "sha256", "size")
    private const val SHA256_PREFIX = "sha256:"
    private val ALPINE_FIELD = Regex("""^  ([a-z0-9_]+): (.*)$""")
    private val UBUNTU_LINE =
        Regex("""([0-9a-fA-F]{64}) \*ubuntu-base-([0-9][0-9.]*)-base-([a-z0-9]+)\.tar\.gz""")

    @Serializable
    private data class OciManifest(val layers: List<OciLayer> = emptyList())

    @Serializable
    private data class OciLayer(val digest: String, val size: Long)
}
