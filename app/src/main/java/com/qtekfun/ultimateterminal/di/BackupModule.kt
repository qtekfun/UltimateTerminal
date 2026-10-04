// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import com.qtekfun.ultimateterminal.BuildConfig
import com.qtekfun.ultimateterminal.data.backup.BackupExporter
import com.qtekfun.ultimateterminal.data.backup.BackupRepositories
import com.qtekfun.ultimateterminal.data.backup.BackupRestorer
import com.qtekfun.ultimateterminal.data.rootfs.TarGzExtractor
import com.qtekfun.ultimateterminal.domain.backup.StreamRootfsExtractor
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    fun backupExporter(
        repositories: BackupRepositories,
        fileSystem: FileSystemRepository,
        @IoDispatcher io: CoroutineDispatcher
    ) = BackupExporter(repositories, fileSystem, BuildConfig.VERSION_NAME, io)

    @Provides
    @Singleton
    fun streamExtractor(
        fileSystem: FileSystemRepository,
        @IoDispatcher io: CoroutineDispatcher
    ): StreamRootfsExtractor = TarGzExtractor(fileSystem, io)

    @Provides
    @Singleton
    fun backupRestorer(
        repositories: BackupRepositories,
        fileSystem: FileSystemRepository,
        extractor: StreamRootfsExtractor,
        @IoDispatcher io: CoroutineDispatcher
    ) = BackupRestorer(repositories, fileSystem, extractor, io)
}
