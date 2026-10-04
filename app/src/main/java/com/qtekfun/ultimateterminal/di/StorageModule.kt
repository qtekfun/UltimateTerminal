// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import android.content.Context
import com.qtekfun.ultimateterminal.data.storage.NioFileSystemRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.platform.AndroidFreeSpace
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.file.Files
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    /** Root filesystems and backups live under `files/storage`, in the app's private storage. */
    @Provides
    @Singleton
    fun fileSystem(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): FileSystemRepository {
        val root = context.filesDir.toPath().resolve("storage")
        Files.createDirectories(root)
        return NioFileSystemRepository(root, ioDispatcher, AndroidFreeSpace)
    }
}
