// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.data.rootfs.TarBuilder
import java.io.ByteArrayOutputStream

/** A hand-made backup file, so a test controls every byte, including the wrong ones. */
internal class BackupBuilder(private val kind: String = "ALL") {
    private val entries = ArrayList<Pair<String, ByteArray>>()
    private val parts = ArrayList<ManifestPart>()
    private var manifestOverride: ByteArray? = null
    private var password: String? = null

    /** Adds [bytes] as the part [name], listed in the manifest with the right hash (or [hash]). */
    fun part(
        name: String,
        bytes: ByteArray,
        meta: DistroMeta? = null,
        hash: String? = null,
        size: Long? = null
    ) = apply {
        entries.add(name to bytes)
        parts.add(ManifestPart(name, hash ?: sha256Hex(bytes), size ?: bytes.size.toLong(), meta))
    }

    fun config(snapshot: ConfigSnapshot = sampleSnapshot()) =
        part(PartNames.CONFIG, ConfigCodec.encode(snapshot))

    fun config(raw: ByteArray) = part(PartNames.CONFIG, raw)

    fun distro(index: Int, rootfs: ByteArray, name: String = "Alpine", default: Boolean = false) =
        part(PartNames.distro(index), rootfs, DistroMeta(name, "ALPINE", "3.20", "root", default))

    /** An entry in the file that the manifest does not list. */
    fun unlisted(name: String, bytes: ByteArray) = apply { entries.add(name to bytes) }

    fun rawManifest(bytes: ByteArray) = apply { manifestOverride = bytes }

    /** Lists a part in the manifest that is not in the file. */
    fun missing(name: String) = apply {
        val meta = DistroMeta("Missing", "ALPINE", "3.20", "root", false).takeIf {
            PartNames.isDistro(name)
        }
        parts.add(ManifestPart(name, "0".repeat(64), 1, meta))
    }

    fun encrypted(password: String) = apply { this.password = password }

    fun build(): ByteArray {
        val manifest = manifestOverride
            ?: ManifestCodec.encode(
                BackupManifest(ManifestCodec.FORMAT, "test", kind, "2026-10-04T12:00:00Z", parts)
            )
        val plain = ByteArrayOutputStream()
        ContainerWriter(plain).use { container ->
            container.bytes(PartNames.MANIFEST, manifest)
            entries.forEach { (name, bytes) -> container.bytes(name, bytes) }
        }
        return password?.let { encrypt(plain.toByteArray(), it) } ?: plain.toByteArray()
    }

    companion object {
        /** [plain] sealed as the encrypted format does, whatever it holds. */
        fun encrypt(plain: ByteArray, password: String): ByteArray {
            val header = EncryptionHeader(
                BackupCrypto.MIN_ITERATIONS,
                ByteArray(16) { 3 },
                ByteArray(7) { 4 },
                1024
            )
            val out = ByteArrayOutputStream()
            val key = BackupCrypto.deriveKey(password, header.salt, header.iterations)
            EncryptingOutputStream(out, key, header).use { it.write(plain) }
            return out.toByteArray()
        }

        /** A small valid root filesystem as the gzip tar a distro part holds. */
        fun rootfs(vararg files: Pair<String, String>): ByteArray {
            val tar = TarBuilder().dir("etc")
            files.forEach { (name, content) -> tar.file(name, content) }
            return tar.gzip()
        }

        /** A [rootfs] whose size depends only on the length of its files, not on what they say. */
        fun rootfsStored(vararg files: Pair<String, String>): ByteArray {
            val tar = TarBuilder().dir("etc")
            files.forEach { (name, content) -> tar.file(name, content) }
            return tar.gzipStored()
        }
    }
}
