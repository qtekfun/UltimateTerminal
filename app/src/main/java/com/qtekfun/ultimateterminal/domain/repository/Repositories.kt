// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.repository

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.model.Profile
import com.qtekfun.ultimateterminal.domain.model.SshHost
import kotlinx.coroutines.flow.Flow

/** Metadata of installed distros. The root filesystem itself is handled by [FileSystemRepository]. */
interface DistroRepository {
    fun observeAll(): Flow<List<Distro>>

    suspend fun get(id: Long): Distro?

    suspend fun getDefault(): Distro?

    /** Registers a distro as [DistroState.INSTALLING]; the first one becomes the default. */
    suspend fun add(distro: NewDistro): Outcome<Distro>

    suspend fun rename(id: Long, name: String): Outcome<Unit>

    suspend fun setDefaultUser(id: Long, user: String): Outcome<Unit>

    /** Records an install's progress; [sizeBytes] is kept when null. */
    suspend fun updateState(id: Long, state: DistroState, sizeBytes: Long? = null): Outcome<Unit>

    /** Makes [id] the only default distro. */
    suspend fun setDefault(id: Long): Outcome<Unit>

    /**
     * Removes the metadata of a distro. If it was the default, the oldest remaining distro becomes
     * the default so a new tab always has somewhere to open.
     */
    suspend fun remove(id: Long): Outcome<Unit>
}

interface ProfileRepository {
    fun observeAll(): Flow<List<Profile>>

    suspend fun get(id: Long): Profile?

    suspend fun add(profile: Profile): Outcome<Profile>

    suspend fun update(profile: Profile): Outcome<Unit>

    suspend fun remove(id: Long): Outcome<Unit>
}

interface LayoutRepository {
    fun observeAll(): Flow<List<Layout>>

    suspend fun get(id: Long): Layout?

    suspend fun add(layout: Layout): Outcome<Layout>

    suspend fun update(layout: Layout): Outcome<Unit>

    suspend fun remove(id: Long): Outcome<Unit>
}

interface SshHostRepository {
    fun observeAll(): Flow<List<SshHost>>

    suspend fun get(id: Long): SshHost?

    suspend fun add(host: SshHost): Outcome<SshHost>

    suspend fun update(host: SshHost): Outcome<Unit>

    suspend fun remove(id: Long): Outcome<Unit>
}

interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    /** Applies [transform] to the current settings and stores the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
