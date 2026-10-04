// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageAccess
import com.qtekfun.ultimateterminal.domain.storage.SharedStorageMounts
import com.qtekfun.ultimateterminal.platform.AndroidSharedStorageAccess
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SharedStorageBindings {
    @Binds
    abstract fun access(impl: AndroidSharedStorageAccess): SharedStorageAccess
}

@Module
@InstallIn(SingletonComponent::class)
object SharedStorageModule {
    @Provides
    @Singleton
    fun mounts(fileSystem: FileSystemRepository, access: SharedStorageAccess) =
        SharedStorageMounts(fileSystem, access)
}
