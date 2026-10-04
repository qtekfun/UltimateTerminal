// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import android.content.Context
import com.qtekfun.ultimateterminal.domain.license.LicenseSource
import com.qtekfun.ultimateterminal.domain.license.LicenseTexts
import com.qtekfun.ultimateterminal.platform.AndroidLicenseSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LicenseModule {
    @Provides
    @Singleton
    fun licenseSource(@ApplicationContext context: Context): LicenseSource =
        AndroidLicenseSource(context)

    @Provides
    @Singleton
    fun licenseTexts(source: LicenseSource): LicenseTexts = LicenseTexts(source)
}
