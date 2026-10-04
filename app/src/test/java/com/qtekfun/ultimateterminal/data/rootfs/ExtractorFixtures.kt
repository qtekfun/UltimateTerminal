// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import java.security.MessageDigest

/** Archives shaped like the ones Fedora publishes, built from synthetic content and fixed times. */
internal object ExtractorFixtures {
    // Two JSON blobs named after their own digests, as Fedora's image has them (the names are only
    // names here: nothing checks a JSON blob).
    private const val CONFIG_BLOB =
        "00d66e6ce73d8fd2bc8718e9630cac5e52bcec03a293bf2af6b4cf0ccc83f998"
    private const val MANIFEST_BLOB =
        "bebe5bd9e882469b64cc950da834288bdebca8921ec4bb50646378d672da0ff5"

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** A small Fedora-like root filesystem: relative links, `dnf` as a link, no `resolv.conf`. */
    fun fedoraRootfs(): TarBuilder = TarBuilder()
        .dir("etc")
        .symlink("etc/os-release", "../usr/lib/os-release")
        .dir("usr")
        .dir("usr/lib")
        .file("usr/lib/os-release", "NAME=\"Fedora Linux\"\n")
        .dir("usr/bin")
        .file("usr/bin/dnf5", "ELF", mode = 0b111_101_101)
        .symlink("usr/bin/dnf", "dnf5")
        .symlink("bin", "usr/bin")

    /**
     * The outer tar of an OCI image in Fedora's order: the blobs first, with the config before the
     * layer, and the manifest, `index.json` and `oci-layout` after them.
     * [layers] are `(blob name, bytes)`; the name of an honest blob is the SHA-256 of its bytes.
     */
    fun ociTar(layers: List<Pair<String, ByteArray>>, withJson: Boolean = true): TarBuilder {
        val tar = TarBuilder().dir("blobs").dir("blobs/sha256")
        if (withJson) tar.file("blobs/sha256/$CONFIG_BLOB", """{"architecture":"arm64"}""")
        layers.forEach { (name, bytes) -> tar.fileBytes("blobs/sha256/$name", bytes) }
        if (withJson) {
            tar.file("blobs/sha256/$MANIFEST_BLOB", """{"schemaVersion":2,"layers":[]}""")
            tar.file("index.json", """{"schemaVersion":2,"manifests":[]}""")
            tar.file("oci-layout", """{"imageLayoutVersion":"1.0.0"}""")
        }
        return tar
    }

    /** A layer with its honest name. */
    fun layer(bytes: ByteArray): Pair<String, ByteArray> = sha256(bytes) to bytes
}
