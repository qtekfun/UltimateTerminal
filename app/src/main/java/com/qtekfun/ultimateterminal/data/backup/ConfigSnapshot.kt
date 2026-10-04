// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.appearance.ExtraKeyStyle
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the app keeps that is worth moving to another device, as `config.json` (SPEC RF-06).
 * Items refer to each other by name or by position, never by database id, because ids differ on
 * another device: a profile names its distro, a layout pane gives the position of its profile in
 * [profiles], and a host names its key by alias (the alias travels with the key).
 *
 * It is decoded leniently (unknown fields are ignored) so a later version can add to it.
 */
@Serializable
internal data class ConfigSnapshot(
    val version: Int = ConfigCodec.VERSION,
    val settings: SettingsDto,
    val distros: List<DistroRefDto> = emptyList(),
    val profiles: List<ProfileDto> = emptyList(),
    val layouts: List<LayoutDto> = emptyList(),
    val sshHosts: List<HostDto> = emptyList(),
    val sshKeys: List<KeyDto> = emptyList()
)

@Serializable
internal data class SettingsDto(
    val themeMode: String,
    val oledBlack: Boolean,
    val dynamicColor: Boolean,
    val keepAwake: Boolean,
    val sharedStorage: Boolean,
    val defaultScrollbackLines: Int,
    val terminalSchemeId: String,
    val terminalFontSizeSp: Float,
    /** The imported color schemes, in the format of a scheme list export. */
    val customSchemes: String,
    /** How the extra-keys row is drawn; a backup from before it existed has none (flat). */
    val extraKeyStyle: String = ExtraKeyStyle.DEFAULT.name
)

/** A distro of the source device, only to find the same one by name on this device. */
@Serializable
internal data class DistroRefDto(val name: String, val isDefault: Boolean)

@Serializable
internal data class ProfileDto(
    val name: String,
    val colorSchemeId: String,
    val fontFamily: String,
    val fontSizeSp: Int,
    val scrollbackLines: Int,
    val distro: String? = null,
    val user: String? = null,
    val startupCommand: String? = null
)

/** In [root], a pane's `profileId` is a position in [ConfigSnapshot.profiles]. */
@Serializable
internal data class LayoutDto(val name: String, val root: LayoutNode)

@Serializable
internal data class HostDto(
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val keyAlias: String? = null,
    val distro: String? = null
)

@Serializable
internal data class KeyDto(
    val alias: String,
    val name: String,
    val type: String,
    val publicKey: String,
    val fingerprint: String,
    val createdAt: String,
    val privateKey: String
)

internal object ConfigCodec {
    const val VERSION = 1
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: ConfigSnapshot): ByteArray =
        json.encodeToString(ConfigSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): BackupResult<ConfigSnapshot> {
        val snapshot = try {
            json.decodeFromString(ConfigSnapshot.serializer(), String(bytes, Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException, and so is a layout whose
            // ratio is out of range.
            return BackupResult.Failure(BackupError.InvalidConfig("not a configuration"))
        }
        return if (snapshot.version == VERSION) {
            BackupResult.Success(snapshot)
        } else {
            BackupResult.Failure(BackupError.UnsupportedVersion(snapshot.version))
        }
    }
}

/** The same tree with every pane's profile reference passed through [map]. */
internal fun LayoutNode.mapProfiles(map: (Long?) -> Long?): LayoutNode = when (this) {
    is LayoutNode.Pane -> copy(profileId = map(profileId))
    is LayoutNode.Split -> copy(first = first.mapProfiles(map), second = second.mapProfiles(map))
}
