// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import com.qtekfun.ultimateterminal.data.repository.RoomDistroRepository
import com.qtekfun.ultimateterminal.data.repository.RoomLayoutRepository
import com.qtekfun.ultimateterminal.data.repository.RoomProfileRepository
import com.qtekfun.ultimateterminal.data.repository.RoomSettingsRepository
import com.qtekfun.ultimateterminal.data.repository.RoomSshHostRepository
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun distros(impl: RoomDistroRepository): DistroRepository

    @Binds
    @Singleton
    abstract fun profiles(impl: RoomProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun layouts(impl: RoomLayoutRepository): LayoutRepository

    @Binds
    @Singleton
    abstract fun sshHosts(impl: RoomSshHostRepository): SshHostRepository

    @Binds
    @Singleton
    abstract fun settings(impl: RoomSettingsRepository): SettingsRepository
}
