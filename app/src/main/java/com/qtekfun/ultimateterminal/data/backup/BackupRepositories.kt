// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.backup

import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore
import javax.inject.Inject

/** Everything a backup reads from or writes to, apart from the files. */
class BackupRepositories @Inject constructor(
    val settings: SettingsRepository,
    val profiles: ProfileRepository,
    val layouts: LayoutRepository,
    val hosts: SshHostRepository,
    val distros: DistroRepository,
    val keys: SshKeyStore
)
