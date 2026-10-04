// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import android.content.Context
import com.qtekfun.ultimateterminal.data.proot.DistroLaunchFactory
import com.qtekfun.ultimateterminal.data.proot.ProotCommandBuilder
import com.qtekfun.ultimateterminal.data.ssh.AesGcmSecretBox
import com.qtekfun.ultimateterminal.data.ssh.FileSshKeyStore
import com.qtekfun.ultimateterminal.data.ssh.JcaSshKeyGenerator
import com.qtekfun.ultimateterminal.data.storage.NioSecretFileStore
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.domain.repository.SecretFileStore
import com.qtekfun.ultimateterminal.domain.repository.SshHostRepository
import com.qtekfun.ultimateterminal.domain.ssh.SecretBox
import com.qtekfun.ultimateterminal.domain.ssh.SshConnector
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyGenerator
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyService
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyStore
import com.qtekfun.ultimateterminal.domain.ssh.TokenSource
import com.qtekfun.ultimateterminal.security.KeystoreSecretKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.file.Files
import java.security.SecureRandom
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Wires SSH hosts and keys, and the proot launch that runs `ssh` in a distro. */
@Module
@InstallIn(SingletonComponent::class)
object SshModule {
    @Provides
    @Singleton
    fun secretFiles(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher
    ): SecretFileStore {
        // The same root as the file system repository: key blobs and the distros share a backup.
        val root = context.filesDir.toPath().resolve("storage")
        Files.createDirectories(root)
        return NioSecretFileStore(root, io)
    }

    @Provides
    @Singleton
    fun secretBox(): SecretBox = AesGcmSecretBox { KeystoreSecretKey.get() }

    @Provides
    @Singleton
    fun tokens(): TokenSource {
        val random = SecureRandom()
        return TokenSource { count ->
            ByteArray(count).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        }
    }

    @Provides
    @Singleton
    fun keyStore(files: SecretFileStore, box: SecretBox, tokens: TokenSource): SshKeyStore =
        FileSshKeyStore(files, box, tokens)

    @Provides
    @Singleton
    fun keyGenerator(): SshKeyGenerator = JcaSshKeyGenerator()

    @Provides
    @Singleton
    fun keyService(
        store: SshKeyStore,
        generator: SshKeyGenerator,
        hosts: SshHostRepository
    ): SshKeyService = SshKeyService(store, generator, hosts)

    @Provides
    @Singleton
    fun connector(
        hosts: SshHostRepository,
        distros: DistroRepository,
        keys: SshKeyStore,
        files: SecretFileStore,
        tokens: TokenSource
    ): SshConnector = SshConnector(hosts, distros, keys, files, tokens)

    @Provides
    @Singleton
    fun launchFactory(
        @ApplicationContext context: Context,
        fileSystem: FileSystemRepository,
        @IoDispatcher io: CoroutineDispatcher
    ): DistroLaunchFactory {
        // proot's temporary files need a private directory; /tmp does not exist on Android.
        val tmp = context.cacheDir.toPath().resolve("proot-tmp")
        Files.createDirectories(tmp)
        val proot = ProotCommandBuilder(context.applicationInfo.nativeLibraryDir, tmp.toString())
        return DistroLaunchFactory(proot, fileSystem, CoroutineScope(SupervisorJob() + io))
    }
}
