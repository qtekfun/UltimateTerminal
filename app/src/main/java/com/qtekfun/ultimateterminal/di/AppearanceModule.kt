// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import com.qtekfun.ultimateterminal.domain.appearance.AppearanceManager
import com.qtekfun.ultimateterminal.domain.appearance.FontFileStore
import com.qtekfun.ultimateterminal.domain.appearance.FontImporter
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.platform.AndroidFontStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID

@Module
@InstallIn(SingletonComponent::class)
object AppearanceModule {
    private const val SUFFIX_LENGTH = 8

    @Provides
    fun fontFileStore(store: AndroidFontStore): FontFileStore = store

    @Provides
    fun appearanceManager(settings: SettingsRepository, store: FontFileStore): AppearanceManager =
        AppearanceManager(
            settings,
            FontImporter(store) { UUID.randomUUID().toString().take(SUFFIX_LENGTH) },
            store
        )
}
