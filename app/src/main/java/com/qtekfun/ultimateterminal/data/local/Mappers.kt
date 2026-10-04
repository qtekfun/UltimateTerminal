// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local

import com.qtekfun.ultimateterminal.data.local.entity.DistroEntity
import com.qtekfun.ultimateterminal.data.local.entity.LayoutEntity
import com.qtekfun.ultimateterminal.data.local.entity.ProfileEntity
import com.qtekfun.ultimateterminal.data.local.entity.SshHostEntity
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SshHost
import java.time.Instant

/**
 * Rows whose content is not valid (a directory outside the storage root, an unreadable layout)
 * map to null and are left out, so a damaged or tampered row, e.g. from a restored backup, can
 * never be used.
 */
internal fun DistroEntity.toDomainOrNull(): Distro? {
    val path = FsPath.of(directory).getOrNull() ?: return null
    return Distro(
        id = id,
        name = name,
        type = type,
        release = release,
        directory = path,
        defaultUser = defaultUser,
        state = state,
        sizeBytes = sizeBytes,
        installedAt = Instant.ofEpochMilli(installedAtMillis),
        isDefault = isDefault
    )
}

internal fun ProfileEntity.toDomain() = Profile(
    id = id,
    name = name,
    colorSchemeId = colorSchemeId,
    fontFamily = fontFamily,
    fontSizeSp = fontSizeSp,
    scrollbackLines = scrollbackLines,
    distroId = distroId,
    user = user,
    startupCommand = startupCommand
)

internal fun Profile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    colorSchemeId = colorSchemeId,
    fontFamily = fontFamily,
    fontSizeSp = fontSizeSp,
    scrollbackLines = scrollbackLines,
    distroId = distroId,
    user = user,
    startupCommand = startupCommand
)

internal fun LayoutEntity.toDomainOrNull(): Layout? =
    LayoutCodec.decode(tree)?.let { Layout(id = id, name = name, root = it) }

internal fun Layout.toEntity() = LayoutEntity(id = id, name = name, tree = LayoutCodec.encode(root))

internal fun SshHostEntity.toDomain() = SshHost(
    id = id,
    name = name,
    host = host,
    port = port,
    user = user,
    keyAlias = keyAlias,
    distroId = distroId
)

internal fun SshHost.toEntity() = SshHostEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    user = user,
    keyAlias = keyAlias,
    distroId = distroId
)
