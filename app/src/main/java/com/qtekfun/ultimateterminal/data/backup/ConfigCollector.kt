// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import kotlinx.coroutines.flow.first

/** Reads the app's configuration from its repositories into a [ConfigSnapshot]. */
internal class ConfigCollector(repositories: BackupRepositories) {
    private val settings = repositories.settings
    private val profiles = repositories.profiles
    private val layouts = repositories.layouts
    private val hosts = repositories.hosts
    private val distros = repositories.distros
    private val keys = repositories.keys

    /** Private keys go into the snapshot only when [includeKeys] is true. */
    suspend fun collect(includeKeys: Boolean): BackupResult<ConfigSnapshot> {
        val allProfiles = profiles.observeAll().first()
        val allDistros = distros.observeAll().first()
        val names = allDistros.associate { it.id to it.name }
        val position = allProfiles.withIndex().associate { it.value.id to it.index.toLong() }
        val exported = if (includeKeys) collectKeys() else BackupResult.Success(emptyList())
        return when (exported) {
            is BackupResult.Failure -> exported

            is BackupResult.Success -> BackupResult.Success(
                ConfigSnapshot(
                    settings = settings.observe().first().toDto(),
                    distros = allDistros.map { DistroRefDto(it.name, it.isDefault) },
                    profiles = allProfiles.map { p ->
                        ProfileDto(
                            p.name,
                            p.colorSchemeId,
                            p.fontFamily,
                            p.fontSizeSp,
                            p.scrollbackLines,
                            p.distroId?.let(names::get),
                            p.user,
                            p.startupCommand
                        )
                    },
                    layouts = layouts.observeAll().first().map { layout ->
                        LayoutDto(
                            layout.name,
                            layout.root.mapProfiles { id ->
                                id?.let(position::get)
                            }
                        )
                    },
                    sshHosts = hosts.observeAll().first().map { h ->
                        HostDto(
                            h.name,
                            h.host,
                            h.port,
                            h.user,
                            h.keyAlias,
                            h.distroId?.let(names::get)
                        )
                    },
                    sshKeys = exported.value
                )
            )
        }
    }

    private suspend fun collectKeys(): BackupResult<List<KeyDto>> {
        val exported = ArrayList<KeyDto>()
        for (info in keys.observe().first()) {
            when (val key = keys.privateKey(info.alias)) {
                is SshResult.Success -> exported.add(info.toDto(key.value))

                is SshResult.Failure ->
                    return BackupResult.Failure(
                        BackupError.Io("cannot read the SSH key ${info.alias}")
                    )
            }
        }
        return BackupResult.Success(exported)
    }
}

private fun AppSettings.toDto() = SettingsDto(
    themeMode = themeMode.name,
    oledBlack = oledBlack,
    dynamicColor = dynamicColor,
    keepAwake = keepAwake,
    sharedStorage = sharedStorage,
    defaultScrollbackLines = defaultScrollbackLines,
    terminalSchemeId = terminalSchemeId,
    terminalFontSizeSp = terminalFontSizeSp,
    customSchemes = SchemeCodec.encodeList(customSchemes),
    extraKeyStyle = appearance.extraKeyStyle.name,
    prootCompatibilityMode = prootCompatibilityMode,
    dnsFallbackServers = dnsFallbackServers,
    extraKeys = extraKeys.serialize(),
    shortcuts = ShortcutsDto(bindings = shortcuts.serialize()),
    appearance = AppearanceDto(
        fontId = appearance.fontId,
        lineSpacing = appearance.lineSpacing,
        letterSpacing = appearance.letterSpacing,
        marginDp = appearance.marginDp,
        cursorShape = appearance.cursorShape.name,
        cursorBlink = appearance.cursorBlink,
        chromeStyle = appearance.chromeStyle.name,
        cornerRadiusDp = appearance.cornerRadiusDp
    )
)

private fun SshKeyInfo.toDto(privateKey: String) =
    KeyDto(alias, name, type.name, publicKey, fingerprint, createdAt.toString(), privateKey)
