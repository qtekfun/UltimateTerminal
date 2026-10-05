// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.appearance.ChromeStyle
import com.qtekfun.ultimateterminal.domain.appearance.CursorShape
import com.qtekfun.ultimateterminal.domain.appearance.CustomFont
import com.qtekfun.ultimateterminal.domain.appearance.ExtraKeyStyle
import com.qtekfun.ultimateterminal.domain.appearance.FontCatalog
import com.qtekfun.ultimateterminal.domain.appearance.TerminalAppearance
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SshHost
import com.qtekfun.ultimateterminal.domain.model.ThemeMode
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import com.qtekfun.ultimateterminal.domain.settings.DnsServers
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.domain.terminal.ExtraKeysConfig
import com.qtekfun.ultimateterminal.domain.terminal.FontZoom
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutMap
import com.qtekfun.ultimateterminal.domain.terminal.ShortcutText
import com.qtekfun.ultimateterminal.domain.theme.BuiltInSchemes
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.first

/** How many items of each kind a restore added; what already existed counts in [skipped]. */
internal data class AppliedConfig(
    val profiles: Int,
    val layouts: Int,
    val sshHosts: Int,
    val sshKeys: Int,
    val skipped: Int
)

/**
 * Puts a [ConfigSnapshot] into the app. It only adds: an item whose name (or key alias) is already
 * in use is left as it is, so restoring never deletes or overwrites anything the user has now. The
 * exception is the settings, which are replaced, as the point of a restore is the same look and
 * behavior. The shared-storage switch is not copied: its permission belongs to the device and has
 * to be asked for again (SPEC RF-06).
 */
internal class ConfigApplier(repositories: BackupRepositories) {
    private val settings = repositories.settings
    private val profiles = repositories.profiles
    private val layouts = repositories.layouts
    private val hosts = repositories.hosts
    private val distros = repositories.distros
    private val keys = repositories.keys

    /** [restored] maps the name a distro had in the backup to its id here, ahead of same-named ones. */
    suspend fun apply(snapshot: ConfigSnapshot, restored: Map<String, Long>): AppliedConfig {
        val distroIds = distros.observeAll().first()
            .associate { it.name.lowercase() to it.id } + restored.mapKeys { it.key.lowercase() }
        val addedKeys = restoreKeys(snapshot.sshKeys)
        val profileIds = restoreProfiles(snapshot.profiles, distroIds)
        val layoutCount = restoreLayouts(snapshot.layouts, profileIds.ids)
        val hostCount = restoreHosts(snapshot.sshHosts, distroIds)
        applySettings(snapshot.settings)
        snapshot.distros.firstOrNull { it.isDefault }
            ?.let { distroIds[it.name.lowercase()] }
            ?.let { distros.setDefault(it) }
        val skipped =
            addedKeys.skipped + profileIds.skipped + layoutCount.skipped + hostCount.skipped
        return AppliedConfig(
            profiles = profileIds.added,
            layouts = layoutCount.added,
            sshHosts = hostCount.added,
            sshKeys = addedKeys.added,
            skipped = skipped
        )
    }

    private class Tally(val added: Int, val skipped: Int)

    private class ProfileIds(val ids: List<Long?>, val added: Int, val skipped: Int)

    private suspend fun restoreKeys(dtos: List<KeyDto>): Tally {
        val known = keys.observe().first().map { it.alias }.toSet()
        var added = 0
        for (dto in dtos) {
            val info = dto.takeIf { it.alias !in known }?.toInfo()
            val stored = info?.let { keys.put(it, dto.privateKey) }
            if (stored is SshResult.Success) added++
        }
        return Tally(added, dtos.size - added)
    }

    private suspend fun restoreProfiles(
        dtos: List<ProfileDto>,
        distroIds: Map<String, Long>
    ): ProfileIds {
        val existing = profiles.observeAll().first().associateBy { it.name.lowercase() }
        val ids = ArrayList<Long?>()
        var added = 0
        for (dto in dtos) {
            val taken = existing[dto.name.lowercase()]
            val created = if (taken == null) profiles.add(dto.toProfile(distroIds)) else null
            if (created is Outcome.Success) added++
            ids.add(
                when {
                    taken != null -> taken.id
                    created is Outcome.Success -> created.value.id
                    else -> null
                }
            )
        }
        return ProfileIds(ids, added, dtos.size - added)
    }

    private suspend fun restoreLayouts(dtos: List<LayoutDto>, profileIds: List<Long?>): Tally {
        val existing = layouts.observeAll().first().map { it.name.lowercase() }.toSet()
        var added = 0
        for (dto in dtos) {
            val root = dto.root.mapProfiles { position ->
                position?.let { profileIds.getOrNull(it.toInt()) }
            }
            val created = if (dto.name.lowercase() in
                existing
            ) {
                null
            } else {
                layouts.add(Layout(name = dto.name, root = root))
            }
            if (created is Outcome.Success) added++
        }
        return Tally(added, dtos.size - added)
    }

    private suspend fun restoreHosts(dtos: List<HostDto>, distroIds: Map<String, Long>): Tally {
        val existing = hosts.observeAll().first().map { it.name.lowercase() }.toSet()
        val aliases = keys.observe().first().map { it.alias }.toSet()
        var added = 0
        for (dto in dtos) {
            val host = SshHost(
                name = dto.name,
                host = dto.host,
                port = dto.port,
                user = dto.user,
                keyAlias = dto.keyAlias?.takeIf { it in aliases },
                distroId = dto.distro?.let { distroIds[it.lowercase()] }
            )
            val created = if (dto.name.lowercase() in existing) null else hosts.add(host)
            if (created is Outcome.Success) added++
        }
        return Tally(added, dtos.size - added)
    }

    private suspend fun applySettings(dto: SettingsDto) {
        val custom = SchemeCodec.decodeList(dto.customSchemes)
        val known = BuiltInSchemes.all.map { it.id } + custom.map { it.id }
        settings.update {
            it.copy(
                themeMode = ThemeMode.entries.firstOrNull { mode -> mode.name == dto.themeMode }
                    ?: ThemeMode.SYSTEM,
                oledBlack = dto.oledBlack,
                dynamicColor = dto.dynamicColor,
                keepAwake = dto.keepAwake,
                // The permission is the device's, not the backup's: it is asked for again.
                sharedStorage = false,
                defaultScrollbackLines =
                    dto.defaultScrollbackLines.coerceIn(Profile.SCROLLBACK_RANGE),
                terminalSchemeId = dto.terminalSchemeId.takeIf { id -> id in known }
                    ?: BuiltInSchemes.DEFAULT_ID,
                terminalFontSizeSp = dto.terminalFontSizeSp.coerceIn(
                    FontZoom.MIN_SP,
                    FontZoom.MAX_SP
                ),
                customSchemes = custom,
                prootCompatibilityMode = dto.prootCompatibilityMode ?: it.prootCompatibilityMode
            ).withLaterSettings(dto)
        }
    }
}

/**
 * The settings that came after the first format (T16). A backup that predates them has none, and
 * then the device keeps what it has; one that has them is read as untrusted: the servers must be
 * addresses, the keys must be in the catalog and the numbers must be in range.
 */
private fun AppSettings.withLaterSettings(dto: SettingsDto): AppSettings {
    val servers = dto.dnsFallbackServers
    val keys = dto.extraKeys
    val look = dto.appearance
    return copy(
        shortcuts = restoredShortcuts(dto.shortcuts, shortcuts),
        dnsFallbackServers = if (servers == null) {
            dnsFallbackServers
        } else {
            DnsServers.parse(DnsServers.format(servers)).servers
        },
        extraKeys = if (keys == null) extraKeys else ExtraKeysConfig.parse(keys).first,
        appearance = (if (look == null) appearance else look.toAppearance(customFonts))
            .copy(extraKeyStyle = ExtraKeyStyle.parse(dto.extraKeyStyle))
    )
}

/**
 * The shortcuts of a backup, or [current] when it brings none that can be used: it predates them,
 * it was written in a format this app does not know, or not one of its lines made sense. A line
 * that does not (a key that does not exist, an action from a later app) is dropped and the rest is
 * kept, so a partly readable backup is not lost.
 */
private fun restoredShortcuts(dto: ShortcutsDto?, current: ShortcutMap): ShortcutMap {
    val readable = dto?.takeIf { it.version == ShortcutsDto.VERSION }
        ?.let { ShortcutText.analyze(it.bindings).map }
    return readable?.takeIf { it.all.isNotEmpty() } ?: current
}

private fun ProfileDto.toProfile(distroIds: Map<String, Long>) = Profile(
    name = name,
    colorSchemeId = colorSchemeId,
    fontFamily = fontFamily,
    fontSizeSp = fontSizeSp,
    scrollbackLines = scrollbackLines,
    distroId = distro?.let { distroIds[it.lowercase()] },
    user = user,
    startupCommand = startupCommand
)

private fun KeyDto.toInfo(): SshKeyInfo? {
    val keyType = SshKeyType.entries.firstOrNull { it.name == type }
    val created = try {
        Instant.parse(createdAt)
    } catch (_: DateTimeParseException) {
        null
    }
    return if (keyType == null || created == null) {
        null
    } else {
        SshKeyInfo(alias, name, keyType, publicKey, fingerprint, created)
    }
}

/** The appearance of the backup; a font this device does not have, or a name it does not know, falls back. */
private fun AppearanceDto.toAppearance(fonts: List<CustomFont>): TerminalAppearance {
    val defaults = TerminalAppearance()
    return TerminalAppearance(
        fontId = FontCatalog.idOrBundled(fontId, fonts),
        lineSpacing = lineSpacing,
        letterSpacing = letterSpacing,
        marginDp = marginDp,
        cursorShape =
            CursorShape.entries.firstOrNull { it.name == cursorShape } ?: defaults.cursorShape,
        cursorBlink = cursorBlink,
        chromeStyle =
            ChromeStyle.entries.firstOrNull { it.name == chromeStyle } ?: defaults.chromeStyle,
        cornerRadiusDp = cornerRadiusDp
    ).sanitized()
}
