// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import android.os.Build
import com.qtekfun.ultimateterminal.data.rootfs.HttpRootfsDownloader
import com.qtekfun.ultimateterminal.data.rootfs.OfficialRootfsCatalog
import com.qtekfun.ultimateterminal.data.rootfs.TarGzExtractor
import com.qtekfun.ultimateterminal.domain.distro.DistroInstaller
import com.qtekfun.ultimateterminal.domain.distro.DistroManager
import com.qtekfun.ultimateterminal.domain.distro.RootfsExtractor
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsCatalog
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

/** Wires the distro install pipeline: official catalog, downloader, extractor, installer. */
@Module
@InstallIn(SingletonComponent::class)
object DistroModule {
    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun rootfsCatalog(client: OkHttpClient, @IoDispatcher io: CoroutineDispatcher): RootfsCatalog =
        OfficialRootfsCatalog(client, io = io)

    @Provides
    @Singleton
    fun rootfsDownloader(
        client: OkHttpClient,
        @IoDispatcher io: CoroutineDispatcher
    ): RootfsDownloader = HttpRootfsDownloader(client, io = io)

    @Provides
    @Singleton
    fun rootfsExtractor(
        fileSystem: FileSystemRepository,
        @IoDispatcher io: CoroutineDispatcher
    ): RootfsExtractor = TarGzExtractor(fileSystem, io)

    @Provides
    @Singleton
    fun distroInstaller(
        catalog: RootfsCatalog,
        downloader: RootfsDownloader,
        extractor: RootfsExtractor,
        fileSystem: FileSystemRepository,
        distros: DistroRepository
    ): DistroInstaller = DistroInstaller(
        catalog = catalog,
        downloader = downloader,
        extractor = extractor,
        fileSystem = fileSystem,
        distros = distros,
        architecture = { Architecture.fromAbis(Build.SUPPORTED_ABIS.toList()) },
        supportedAbis = { Build.SUPPORTED_ABIS.toList() }
    )

    @Provides
    @Singleton
    fun distroManager(distros: DistroRepository, fileSystem: FileSystemRepository): DistroManager =
        DistroManager(distros, fileSystem)
}
