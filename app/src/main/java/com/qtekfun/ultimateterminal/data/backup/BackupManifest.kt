// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupKind
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.model.DistroType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The name of every part a backup may hold; anything else in the file is refused. */
internal object PartNames {
    const val MANIFEST = "manifest.json"
    const val CONFIG = "config.json"
    private val distro = Regex("distros/[0-9]{1,4}\\.tar\\.gz")

    fun distro(index: Int): String = "distros/$index.tar.gz"

    fun isDistro(name: String): Boolean = distro.matches(name)

    fun isKnown(name: String): Boolean = name == MANIFEST || name == CONFIG || isDistro(name)
}

/** What the restore needs to register a distro again. */
@Serializable
internal data class DistroMeta(
    val name: String,
    val type: String,
    val release: String,
    val user: String,
    val isDefault: Boolean
)

@Serializable
internal data class ManifestPart(
    val name: String,
    val sha256: String,
    val size: Long,
    val distro: DistroMeta? = null
)

/**
 * The first entry of a backup: what it holds and the SHA-256 of every part, so a restore can check
 * the whole file before it changes anything.
 */
@Serializable
internal data class BackupManifest(
    val format: Int,
    val app: String,
    val kind: String,
    val createdAt: String,
    val parts: List<ManifestPart>
) {
    val backupKind: BackupKind get() = BackupKind.valueOf(kind)
}

internal object ManifestCodec {
    const val FORMAT = 1
    const val MAX_BYTES = 1024 * 1024
    private const val MAX_DISTROS = 1000
    private val hash = Regex("[0-9a-f]{64}")
    private val json = Json { encodeDefaults = true }

    fun encode(manifest: BackupManifest): ByteArray =
        json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): BackupResult<BackupManifest> {
        val manifest = try {
            json.decodeFromString(BackupManifest.serializer(), String(bytes, Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            return invalid("not a manifest")
        }
        return if (manifest.format == FORMAT) {
            checked(manifest)
        } else {
            BackupResult.Failure(BackupError.UnsupportedVersion(manifest.format))
        }
    }

    private fun checked(manifest: BackupManifest): BackupResult<BackupManifest> {
        val problem = validate(manifest)
        return if (problem == null) BackupResult.Success(manifest) else invalid(problem)
    }

    private fun invalid(reason: String) = BackupResult.Failure(BackupError.InvalidManifest(reason))

    /** The first thing wrong with a manifest, or null when it is consistent. */
    private fun validate(manifest: BackupManifest): String? {
        val kind = BackupKind.entries.firstOrNull { it.name == manifest.kind }
        return when {
            kind == null -> "unknown kind"
            else -> validateParts(manifest.parts) ?: validateKind(kind, manifest.parts)
        }
    }

    private fun validateParts(parts: List<ManifestPart>): String? {
        val names = parts.map { it.name }
        val distros = parts.filter { PartNames.isDistro(it.name) }
        return when {
            names.toSet().size != names.size -> "a part is listed twice"

            names.any { it != PartNames.CONFIG && !PartNames.isDistro(it) } -> "unknown part"

            parts.any { !hash.matches(it.sha256) || it.size < 0 } -> "bad hash or size"

            distros.size > MAX_DISTROS -> "too many distros"

            distros.any { !validDistro(it.distro) } -> "bad distro description"

            parts.any { it.name == PartNames.CONFIG && it.distro != null } ->
                "the configuration is not a distro"

            else -> null
        }
    }

    /** Each kind of backup holds a fixed set of parts. */
    private fun validateKind(kind: BackupKind, parts: List<ManifestPart>): String? {
        val distros = parts.count { PartNames.isDistro(it.name) }
        val hasConfig = parts.any { it.name == PartNames.CONFIG }
        return when {
            kind == BackupKind.CONFIG && (!hasConfig || distros > 0) ->
                "a configuration backup holds only the configuration"

            kind == BackupKind.DISTRO && (hasConfig || distros != 1) ->
                "a distro backup holds exactly one distro"

            kind == BackupKind.ALL && !hasConfig -> "a full backup holds the configuration"

            else -> null
        }
    }

    private fun validDistro(meta: DistroMeta?): Boolean =
        meta != null && DistroType.entries.any { it.name == meta.type }
}
