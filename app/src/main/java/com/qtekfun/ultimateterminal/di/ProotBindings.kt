// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import com.qtekfun.ultimateterminal.data.proot.ProotRuntime
import com.qtekfun.ultimateterminal.data.proot.ResolvConfSource
import com.qtekfun.ultimateterminal.platform.AndroidProotRuntime
import com.qtekfun.ultimateterminal.platform.AndroidResolvConfSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What proot needs from the device; the decisions are made in `ProotSessionPlanner`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProotBindings {
    @Binds
    abstract fun runtime(impl: AndroidProotRuntime): ProotRuntime

    @Binds
    abstract fun resolvConf(impl: AndroidResolvConfSource): ResolvConfSource
}
